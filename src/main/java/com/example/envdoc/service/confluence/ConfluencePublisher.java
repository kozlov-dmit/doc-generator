package com.example.envdoc.service.confluence;

import com.example.envdoc.config.ConfluenceConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Base64;

/**
 * Сервис для публикации документации в Confluence.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "confluence.enabled", havingValue = "true")
public class ConfluencePublisher {

    private final ConfluenceConfig config;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public ConfluencePublisher(ConfluenceConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();

        String credentials = config.getUsername() + ":" + config.getToken();
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());

        this.webClient = WebClient.builder()
                .baseUrl(config.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodedCredentials)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    /**
     * Публикует документацию в Confluence.
     *
     * @param markdownContent содержимое в Markdown
     * @param title           заголовок страницы
     * @param spaceKey        ключ пространства
     * @param parentPageId    ID родительской страницы (опционально)
     * @return URL созданной/обновлённой страницы
     */
    public String publish(String markdownContent, String title, String spaceKey, String parentPageId) {
        log.info("Publishing to Confluence: space={}, title={}", spaceKey, title);

        // Конвертируем Markdown в Confluence Storage Format
        String storageFormat = convertMarkdownToStorageFormat(markdownContent);

        try {
            // Проверяем существование страницы
            String existingPageId = findPageByTitle(spaceKey, title);

            if (existingPageId != null) {
                // Обновляем существующую страницу
                return updatePage(existingPageId, title, storageFormat);
            } else {
                // Создаём новую страницу
                return createPage(spaceKey, parentPageId, title, storageFormat);
            }
        } catch (Exception e) {
            log.error("Failed to publish to Confluence: {}", e.getMessage(), e);
            throw new RuntimeException("Confluence publishing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Ищет страницу по заголовку в пространстве.
     */
    private String findPageByTitle(String spaceKey, String title) {
        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/wiki/rest/api/content")
                            .queryParam("spaceKey", spaceKey)
                            .queryParam("title", title)
                            .queryParam("expand", "version")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                    .block();

            JsonNode json = objectMapper.readTree(response);
            JsonNode results = json.get("results");

            if (results != null && results.isArray() && results.size() > 0) {
                return results.get(0).get("id").asText();
            }

            return null;

        } catch (Exception e) {
            log.warn("Error searching for page: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Создаёт новую страницу в Confluence.
     */
    private String createPage(String spaceKey, String parentPageId, String title, String content) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("type", "page");
            requestBody.put("title", title);

            ObjectNode space = objectMapper.createObjectNode();
            space.put("key", spaceKey);
            requestBody.set("space", space);

            if (parentPageId != null && !parentPageId.isBlank()) {
                ArrayNode ancestors = objectMapper.createArrayNode();
                ObjectNode ancestor = objectMapper.createObjectNode();
                ancestor.put("id", parentPageId);
                ancestors.add(ancestor);
                requestBody.set("ancestors", ancestors);
            }

            ObjectNode body = objectMapper.createObjectNode();
            ObjectNode storage = objectMapper.createObjectNode();
            storage.put("value", content);
            storage.put("representation", "storage");
            body.set("storage", storage);
            requestBody.set("body", body);

            String response = webClient.post()
                    .uri("/wiki/rest/api/content")
                    .bodyValue(objectMapper.writeValueAsString(requestBody))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                    .block();

            JsonNode json = objectMapper.readTree(response);
            String pageUrl = config.getBaseUrl() + "/wiki" + json.get("_links").get("webui").asText();

            log.info("Created Confluence page: {}", pageUrl);
            return pageUrl;

        } catch (Exception e) {
            throw new RuntimeException("Failed to create Confluence page: " + e.getMessage(), e);
        }
    }

    /**
     * Обновляет существующую страницу в Confluence.
     */
    private String updatePage(String pageId, String title, String content) {
        try {
            // Получаем текущую версию страницы
            String pageResponse = webClient.get()
                    .uri("/wiki/rest/api/content/" + pageId + "?expand=version")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                    .block();

            JsonNode pageJson = objectMapper.readTree(pageResponse);
            int currentVersion = pageJson.get("version").get("number").asInt();

            // Обновляем страницу
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("type", "page");
            requestBody.put("title", title);

            ObjectNode version = objectMapper.createObjectNode();
            version.put("number", currentVersion + 1);
            requestBody.set("version", version);

            ObjectNode body = objectMapper.createObjectNode();
            ObjectNode storage = objectMapper.createObjectNode();
            storage.put("value", content);
            storage.put("representation", "storage");
            body.set("storage", storage);
            requestBody.set("body", body);

            String response = webClient.put()
                    .uri("/wiki/rest/api/content/" + pageId)
                    .bodyValue(objectMapper.writeValueAsString(requestBody))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                    .block();

            JsonNode json = objectMapper.readTree(response);
            String pageUrl = config.getBaseUrl() + "/wiki" + json.get("_links").get("webui").asText();

            log.info("Updated Confluence page: {}", pageUrl);
            return pageUrl;

        } catch (Exception e) {
            throw new RuntimeException("Failed to update Confluence page: " + e.getMessage(), e);
        }
    }

    /**
     * Конвертирует Markdown в Confluence Storage Format (XHTML).
     */
    private String convertMarkdownToStorageFormat(String markdown) {
        StringBuilder html = new StringBuilder();

        String[] lines = markdown.split("\n");
        boolean inCodeBlock = false;
        boolean inTable = false;
        String codeLanguage = "";

        for (String line : lines) {
            // Code blocks
            if (line.startsWith("```")) {
                if (!inCodeBlock) {
                    inCodeBlock = true;
                    codeLanguage = line.substring(3).trim();
                    if (codeLanguage.isEmpty()) {
                        codeLanguage = "none";
                    }
                    html.append("<ac:structured-macro ac:name=\"code\">");
                    html.append("<ac:parameter ac:name=\"language\">").append(escapeXml(codeLanguage)).append("</ac:parameter>");
                    html.append("<ac:plain-text-body><![CDATA[");
                } else {
                    inCodeBlock = false;
                    html.append("]]></ac:plain-text-body></ac:structured-macro>");
                }
                continue;
            }

            if (inCodeBlock) {
                html.append(line).append("\n");
                continue;
            }

            // Tables
            if (line.startsWith("|")) {
                if (!inTable) {
                    inTable = true;
                    html.append("<table><tbody>");
                }

                // Skip separator line
                if (line.matches("\\|[-:|\\s]+\\|")) {
                    continue;
                }

                html.append("<tr>");
                String[] cells = line.split("\\|");
                for (int i = 1; i < cells.length; i++) {
                    String cell = cells[i].trim();
                    html.append("<td>")
                        .append(convertInlineMarkdown(escapeXml(cell)))
                        .append("</td>");
                }
                html.append("</tr>");
                continue;
            } else if (inTable) {
                inTable = false;
                html.append("</tbody></table>");
            }

            // Headers
            if (line.startsWith("# ")) {
                html.append("<h1>").append(escapeXml(line.substring(2))).append("</h1>");
            } else if (line.startsWith("## ")) {
                html.append("<h2>").append(escapeXml(line.substring(3))).append("</h2>");
            } else if (line.startsWith("### ")) {
                html.append("<h3>").append(escapeXml(line.substring(4))).append("</h3>");
            } else if (line.startsWith("#### ")) {
                html.append("<h4>").append(escapeXml(line.substring(5))).append("</h4>");
            }
            // Blockquote
            else if (line.startsWith("> ")) {
                html.append("<blockquote><p>")
                    .append(convertInlineMarkdown(escapeXml(line.substring(2))))
                    .append("</p></blockquote>");
            }
            // Horizontal rule
            else if (line.equals("---") || line.equals("***")) {
                html.append("<hr/>");
            }
            // List items
            else if (line.startsWith("- ") || line.startsWith("* ")) {
                html.append("<ul><li>")
                    .append(convertInlineMarkdown(escapeXml(line.substring(2))))
                    .append("</li></ul>");
            }
            // Regular paragraph
            else if (!line.trim().isEmpty()) {
                html.append("<p>")
                    .append(convertInlineMarkdown(escapeXml(line)))
                    .append("</p>");
            }
        }

        // Close any open table
        if (inTable) {
            html.append("</tbody></table>");
        }

        return html.toString();
    }

    /**
     * Конвертирует inline Markdown элементы (bold, italic, code).
     */
    private String convertInlineMarkdown(String text) {
        // Inline code
        text = text.replaceAll("`([^`]+)`", "<code>$1</code>");

        // Bold
        text = text.replaceAll("\\*\\*([^*]+)\\*\\*", "<strong>$1</strong>");

        // Italic
        text = text.replaceAll("\\*([^*]+)\\*", "<em>$1</em>");

        return text;
    }

    /**
     * Экранирует специальные XML символы.
     */
    private String escapeXml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
