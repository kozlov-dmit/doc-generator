package com.example.envdoc.service.documentation;

import chat.giga.langchain4j.GigaChatChatModel;
import com.example.envdoc.model.EnvVariable;
import com.example.envdoc.tools.ClassCodeTool;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.agentexecutor.AgentExecutor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Генерация документации через AgentExecutor.
 */
@Service
public class GigaChatAgentDocumentationService {

    private final GigaChatPromptBuilder promptBuilder;
    private final ClassCodeTool classCodeTool;
    private final GigaChatChatModel chatModel;

    public GigaChatAgentDocumentationService(GigaChatPromptBuilder promptBuilder,
                                             ClassCodeTool classCodeTool,
                                             Optional<GigaChatChatModel> chatModel) {
        this.promptBuilder = promptBuilder;
        this.classCodeTool = classCodeTool;
        this.chatModel = chatModel.orElse(null);
    }

    public String generateDocumentation(List<EnvVariable> variables, String projectName, Path repoPath)
            throws Exception {
        if (chatModel == null) {
            return null;
        }

        classCodeTool.setRepoPath(repoPath);

        String prompt = promptBuilder.buildPrompt(variables, projectName);

        StateGraph<AgentExecutor.State> stateGraph = AgentExecutor.builder()
                .chatModel(chatModel)
                .toolsFromObject(classCodeTool)
                .build();

        CompiledGraph<AgentExecutor.State> agent = stateGraph.compile();

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("messages", List.of(
                SystemMessage.from(promptBuilder.systemMessage()),
                UserMessage.from(prompt)
        ));

        var result = agent.invoke(inputs);
        if (result.isPresent()) {
            var lastMessage = result.get().lastMessage();
            if (lastMessage.isPresent() && lastMessage.get() instanceof AiMessage aiMessage) {
                return aiMessage.text();
            }
        }
        return null;
    }
}
