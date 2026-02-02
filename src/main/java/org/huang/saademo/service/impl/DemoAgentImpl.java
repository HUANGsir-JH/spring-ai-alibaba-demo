package org.huang.saademo.service.impl;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.huang.saademo.config.ApiKeyConfig;
import org.huang.saademo.service.DemoAgent;
import org.huang.saademo.tools.UserLocationTool;
import org.huang.saademo.tools.WeatherSearchTool;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Optional;

@Slf4j
@Service
public class DemoAgentImpl implements DemoAgent {
    @Resource
    private ApiKeyConfig apiKeyConfig;
    
    private static final String MODEL = "qwen-plus-latest";
    
    @Override
    public String agentInvoke(String input) throws GraphRunnerException {
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
        
        ModelCallLimitHook hook = ModelCallLimitHook.builder()
                .runLimit(5)
                .exitBehavior(ModelCallLimitHook.ExitBehavior.ERROR) // 超出限制时抛出异常
                .build();
        
        HumanInTheLoopHook human = HumanInTheLoopHook.builder()
                .approvalOn("searchWeather", ToolConfig.builder().description("Please approve the use of the weather search tool.").build())
                .build();
        
        ReactAgent agent = ReactAgent.builder()
                .name("weather-agent")
                .model(model)
                .methodTools(new WeatherSearchTool()) // 传入带有 @Tool 注解的方法所在的类实例，方法名即为工具名
                .hooks(hook,human)
                .systemPrompt("You are a helpful assistant that provides weather information using the weather search tool.")
                .saver(new MemorySaver())
                .build();
        
        Optional<OverAllState> res = agent.invoke(input);
        if(res.isPresent()){
            OverAllState state = res.get();
            System.out.println("Full State:\n " + state);
            ArrayList<Object> messages = state.value("messages", new ArrayList<>());
            for (Object msg : messages) {
                if (msg instanceof AssistantMessage assistantMessage) {
                    return assistantMessage.getText();
                }
            }
        }
        return "No response from agent.";
    }
    
    @Override
    public ReactAgent genAgent() {
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
        
        ReactAgent agent = ReactAgent.builder()
                .name("weather-agent")
                .model(model)
                .methodTools(new WeatherSearchTool(),new UserLocationTool()) // 传入带有 @Tool 注解
                .systemPrompt(getSystemPrompt())
                .outputSchema(getOutputSchema()) // 定义输出格式
                .saver(new MemorySaver())
                .build();
                
        return agent;
    }
    
    @Override
    public ReactAgent genWriterAgent() {
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
        
        ReactAgent agent = ReactAgent.builder()
                .name("writer-agent")
                .model(model)
                .systemPrompt("You are a creative writer who writes engaging stories.")
                .saver(new MemorySaver())
                .build();
        
        return agent;
    }
    
    private String getSystemPrompt(){
        return """
        You are an expert weather forecaster, who speaks in English.
        
        You have access to two tools:
        
        - get_weather_for_location: use this to get the weather for a specific location
        - get_user_location: use this to get the user's location
        
        If a user asks you for the weather, make sure you know the location.
        If you can tell from the question that they mean wherever they are,
        use the get_user_location tool to find their location.
        """;
    }
    
    private String getOutputSchema(){
        return """
        The output should be in JSON format with the following schema:
        {
            "thought": "your thought process here",
            "action": "the action to take, must be one of [get_weather_for_location, get_user_location]",
            "action_input": "the input to the action",
            "observation": "the result of the action",
            "final_answer": "your final answer to the user, if you have enough information"
        }
        """;
    }
}
