package org.huang.saademo.interceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ToolRecordInterceptor extends ToolInterceptor {
    @Override
    public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
        long startTime = System.currentTimeMillis();
        System.out.println("=== Tool Call Start at Time: " + startTime + " ===");
        String arguments = request.getArguments();
        Map<String, Object> context = request.getContext();
        String toolCallId = request.getToolCallId();
        String toolName = request.getToolName();
        
        // 输出请求的详细信息
        System.out.println("Tool Name: " + toolName);
        System.out.println("Tool Call ID: " + toolCallId);
        System.out.println("Arguments: " + arguments);
        System.out.println("Context: " + context);
        
        ToolCallResponse response = handler.call(request);
        
        long endTime = System.currentTimeMillis();
        System.out.println("=== Tool Call End at Time: " + endTime + " ===");
        
        long duration = endTime - startTime;
        System.out.println("=== Tool Call Duration: " + duration + " ms ===");
        
        return response;
    }
    
    @Override
    public String getName() {
        return "ToolRecordInterceptor";
    }
}
