package org.huang.saademo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.huang.saademo.config.ApiKeyConfig;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class RAGAgentService {
    @Resource
    private ApiKeyConfig apiKeyConfig;
    
    @Resource(name= "streamAgentTaskExecutor")
    private ThreadPoolTaskExecutor executor;
    
    private static final String MODEL_NAME = "qwen3.5-plus";
    
    public record AgentOutput(String outputType, String agentName, String type, String text, Map<String, Object> metadata) {}
    
    private ObjectMapper objectMapper = new ObjectMapper();
    
    
    
}
