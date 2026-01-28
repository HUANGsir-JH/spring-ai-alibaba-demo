package org.huang.saademo.service.impl;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.huang.saademo.config.ApiKeyConfig;
import org.huang.saademo.service.DemoAgent;
import org.huang.saademo.tools.WeatherSearchTool;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DemoAgentImpl implements DemoAgent {
    @Resource
    private ApiKeyConfig apiKeyConfig;
    
    private static final String MODEL = "qwen-plus-latest";
    
    @Override
    public String agentInvoke(String input) {
        // 初始化 DashScopeApi 客户端
        DashScopeApi api = DashScopeApi.builder()
                .apiKey(apiKeyConfig.getQwenKey())
                .build();
        
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .model(MODEL)
                .build();
        
        DashScopeChatModel model = DashScopeChatModel.builder()
                .dashScopeApi(api)
                .defaultOptions(options)
                .build();
        
        // 创建工具回调
//        ToolCallback toolCallback = FunctionToolCallback.builder("weather_search_tool", city ->{
//            WeatherSearchTool tool = new WeatherSearchTool();
//            return tool.searchWeather((String) city);
//        }).description("A tool to search for current weather information in a specified city.")
//                .inputType(String.class)
//                .build();
        
        ReactAgent agent = ReactAgent.builder()
                .name("weather-agent")
                .model(model)
//                .tools(toolCallback)
                .methodTools(new WeatherSearchTool()) // 传入带有 @Tool 注解的方法所在的类实例
                .systemPrompt("You are a helpful assistant that provides weather information using the weather search tool.")
                .saver(new MemorySaver())
                .build();
        
        AssistantMessage message = null;
        try {
            message = agent.call(input);
        } catch (GraphRunnerException e) {
            throw new RuntimeException(e.getMessage());
        }
        
        System.out.println(message);
        
        return message.getText();
    }
}
