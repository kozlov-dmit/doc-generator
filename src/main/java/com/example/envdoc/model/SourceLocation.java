package com.example.envdoc.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Местоположение в исходном коде.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceLocation {
    /**
     * Путь к файлу относительно корня репозитория
     */
    private String filePath;

    /**
     * Номер строки (начиная с 1)
     */
    private int lineNumber;

    /**
     * Номер колонки (начиная с 1)
     */
    private int columnNumber;

    @Override
    public String toString() {
        return filePath + ":" + lineNumber;
    }
}
