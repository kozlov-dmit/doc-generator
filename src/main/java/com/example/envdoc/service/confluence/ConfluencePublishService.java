package com.example.envdoc.service.confluence;

import com.example.envdoc.dto.AnalysisRequest;
import com.example.envdoc.model.AnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Публикация результатов в Confluence.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfluencePublishService {

    private final Optional<ConfluencePublisher> confluencePublisher;

    public boolean canPublish() {
        return confluencePublisher.isPresent();
    }

    public void publish(AnalysisResult result, AnalysisRequest request) {
        if (confluencePublisher.isEmpty()) {
            log.warn("Confluence publisher not available");
            return;
        }

        AnalysisRequest.ConfluenceConfig confConfig = request.getConfluenceConfig();
        String spaceKey = confConfig != null && confConfig.getSpaceKey() != null
                ? confConfig.getSpaceKey()
                : "DOCS";

        String parentPageId = confConfig != null ? confConfig.getParentPageId() : null;

        String title = confConfig != null && confConfig.getPageTitle() != null
                ? confConfig.getPageTitle()
                : "Переменные окружения: " + result.getProjectName();

        String pageUrl = confluencePublisher.get().publish(
                result.getMarkdownContent(),
                title,
                spaceKey,
                parentPageId
        );

        result.setConfluencePageUrl(pageUrl);
    }
}
