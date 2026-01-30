package com.example.envdoc.service;

/**
 * Колбэк прогресса анализа.
 */
@FunctionalInterface
public interface AnalysisProgressListener {
    void onProgress(int progress, String step);
}
