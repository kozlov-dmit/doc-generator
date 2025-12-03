package com.example.demo.service;

import com.example.demo.config.FeatureProperties;
import org.springframework.stereotype.Service;

@Service
public class FeatureService {
    private final FeatureProperties featureProperties;

    public FeatureService(FeatureProperties featureProperties) {
        this.featureProperties = featureProperties;
    }

    public String buildMessage() {
        if (!featureProperties.isEnabled()) {
            return "feature is disabled";
        }
        return "url=" + featureProperties.getUrl() + ", timeout=" + featureProperties.getTimeoutMs();
    }
}
