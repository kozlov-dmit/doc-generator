package com.example.envdoc.service.documentation;

import com.example.envdoc.model.EnvVariable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNull;

class GigaChatSimpleDocumentationServiceTest {

    @Test
    void shouldReturnNullWhenModelMissing() {
        GigaChatPromptBuilder promptBuilder = new GigaChatPromptBuilder();
        GigaChatSimpleDocumentationService service =
                new GigaChatSimpleDocumentationService(promptBuilder, Optional.empty());

        String result = service.generateDocumentation(List.of(EnvVariable.builder()
                .name("TEST_VAR")
                .required(true)
                .build()), "test-project");

        assertNull(result);
    }
}
