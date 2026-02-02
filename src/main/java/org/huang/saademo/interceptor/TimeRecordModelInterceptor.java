package org.huang.saademo.interceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class TimeRecordModelInterceptor extends ModelInterceptor {
    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        long startTime = System.currentTimeMillis();
        log.info("=== Model Call Start at Time: {} ===", startTime);
        SystemMessage systemMessage = request.getSystemMessage();
        List<Message> messages = request.getMessages();
        ToolCallingChatOptions options = request.getOptions();
        List<ToolCallback> dynamicToolCallbacks = request.getDynamicToolCallbacks();
        Map<String, Object> context = request.getContext();
        List<String> tools = request.getTools();
        Map<String, String> toolDescriptions = request.getToolDescriptions();
        
        // 输出请求的详细信息
        System.out.println("System Message: " + systemMessage);
        System.out.println("Messages: " + messages);
        System.out.println("Options: " + options);
        System.out.println("Dynamic Tool Callbacks: " + dynamicToolCallbacks);
        System.out.println("Context: " + context);
        System.out.println("Tools: " + tools);
        System.out.println("Tool Descriptions: " + toolDescriptions);
        
        ModelResponse response = handler.call(request);
        
        long endTime = System.currentTimeMillis();
        log.info("=== Model Call End at Time: {} ===", endTime);
        
        long duration = endTime - startTime;
        log.info("=== Model Call Duration: {} ms ===", duration);
        
        return response;
    }
    
    @Override
    public String getName() {
        return "TimeRecordModelInterceptor";
    }
}
