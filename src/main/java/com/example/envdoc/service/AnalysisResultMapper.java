package com.example.envdoc.service;

import com.example.envdoc.dto.AnalysisResponse;
import com.example.envdoc.dto.EnvVariableDto;
import com.example.envdoc.model.AnalysisResult;
import com.example.envdoc.model.EnvVariable;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Маппинг доменной модели в DTO.
 */
@Component
public class AnalysisResultMapper {

    public AnalysisResponse.AnalysisResultDto toDto(AnalysisJob job) {
        AnalysisResult result = job.getResult();

        List<EnvVariableDto> variableDtos = result.getVariables().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        String markdownUrl = result.getMarkdownFilePath() != null
                ? "/api/v1/download/" + job.getId() + "/" +
                  Path.of(result.getMarkdownFilePath()).getFileName()
                : null;

        return AnalysisResponse.AnalysisResultDto.builder()
                .projectName(result.getProjectName())
                .totalVariables(result.getTotalVariables())
                .requiredVariables(result.getRequiredVariables())
                .optionalVariables(result.getOptionalVariables())
                .variables(variableDtos)
                .markdownUrl(markdownUrl)
                .confluencePageUrl(result.getConfluencePageUrl())
                .build();
    }

    private EnvVariableDto toDto(EnvVariable var) {
        EnvVariableDto.DefinitionDto definitionDto = null;
        if (var.getDefinition() != null) {
            definitionDto = EnvVariableDto.DefinitionDto.builder()
                    .type(var.getDefinition().getType())
                    .filePath(var.getDefinition().getFilePath())
                    .lineNumber(var.getDefinition().getLineNumber())
                    .codeSnippet(var.getDefinition().getCodeSnippet())
                    .build();
        }

        List<EnvVariableDto.UsageDto> usageDtos = var.getUsages() != null
                ? var.getUsages().stream()
                        .map(u -> EnvVariableDto.UsageDto.builder()
                                .className(u.getClassName())
                                .methodName(u.getMethodName())
                                .lineNumber(u.getLineNumber())
                                .purpose(u.getPurpose())
                                .context(u.getUsageContext())
                                .build())
                        .collect(Collectors.toList())
                : List.of();

        return EnvVariableDto.builder()
                .name(var.getName())
                .description(var.getDescription())
                .type(var.getDataType())
                .required(var.isRequired())
                .defaultValue(var.getDefaultValue())
                .example(var.getExampleValue())
                .category(var.getCategory())
                .definition(definitionDto)
                .usages(usageDtos)
                .build();
    }
}
