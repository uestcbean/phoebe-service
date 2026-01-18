package com.phoebe.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "dashscope.api")
public class DashScopeConfig {

    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private String key;
    private String model = "qwen-flash";
    private int timeoutSeconds = 60;
    
    // Multimodal models (as specified by user)
    private String visionModel = "qwen-vl-max";  // For image understanding
    private String audioModel = "qwen2-audio-instruct";  // For audio/speech

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getVisionModel() {
        return visionModel;
    }

    public void setVisionModel(String visionModel) {
        this.visionModel = visionModel;
    }

    public String getAudioModel() {
        return audioModel;
    }

    public void setAudioModel(String audioModel) {
        this.audioModel = audioModel;
    }
}

