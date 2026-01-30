package com.example.envdoc.service.documentation;

import chat.giga.langchain4j.GigaChatChatModel;
import com.example.envdoc.model.EnvVariable;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Генерация документации через прямой вызов GigaChat.
 */
@Service
public class GigaChatSimpleDocumentationService {

    private final GigaChatChatModel chatModel;
    private final GigaChatPromptBuilder promptBuilder;

    public GigaChatSimpleDocumentationService(GigaChatPromptBuilder promptBuilder,
                                              Optional<GigaChatChatModel> chatModel) {
        this.promptBuilder = promptBuilder;
        this.chatModel = chatModel.orElse(null);
    }

    public String generateDocumentation(List<EnvVariable> variables, String projectName) {
        if (chatModel == null) {
            return null;
        }

        String prompt = promptBuilder.buildPrompt(variables, projectName);
        List<ChatMessage> messages = List.of(
                SystemMessage.from(promptBuilder.systemMessage()),
                UserMessage.from(prompt)
        );

        var response = chatModel.chat(messages);
        if (response != null && response.aiMessage() != null) {
            return response.aiMessage().text();
        }
        return null;
    }
}
