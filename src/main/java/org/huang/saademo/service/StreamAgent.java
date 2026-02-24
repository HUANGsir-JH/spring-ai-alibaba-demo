package org.huang.saademo.service;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.huang.saademo.config.ApiKeyConfig;
import org.huang.saademo.tools.TimeTool;
import org.huang.saademo.tools.WeatherSearchTool;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

@Service
@Slf4j
public class StreamAgent {
    
    @Resource
    private ApiKeyConfig apiKeyConfig;
    
    @Resource(name= "streamAgentTaskExecutor")
    private ThreadPoolTaskExecutor executor;
    
    private static final String MODEL_NAME = "qwen3-max-2026-01-23";
    
    public void StreamCall(SseEmitter emitter, String prompt) {
        ReactAgent agent = createAgent();
        
        RunnableConfig config = RunnableConfig
                .builder().addMetadata("user_id", "hjh").build();
        
        try{
            executor.submit(()->{
                try {
                    Flux<NodeOutput> stream = agent.stream(prompt, config);
                    stream.subscribe(output->{
                        if(output instanceof StreamingOutput streamingOutput){
                            OutputType type = streamingOutput.getOutputType();
                            Message message = streamingOutput.message();
                            
                            if(type == OutputType.AGENT_MODEL_STREAMING){
                                sendEvent(emitter, "[MODEL]", message.getText());
                            } else if(type == OutputType.AGENT_MODEL_FINISHED) {
                                sendEvent(emitter,"[Done]", "Agent processing completed.");
                                emitter.complete();
                            }
                            
                            if(type == OutputType.AGENT_TOOL_FINISHED) {
                                if(message instanceof ToolResponseMessage toolResponse){
                                    toolResponse.getResponses().forEach(response->{
                                        sendEvent(emitter, "[Tool Result]", response.name() + ": " + response.responseData());
                                    });
                                }
                            }
                        }
                    },error ->{
                        log.error("Error in streaming", error);
                        emitter.completeWithError(error);
                    },()->{
                        sendEvent(emitter, "[Complete]", "Streaming finished.");
                        emitter.complete();
                    });
                } catch (GraphRunnerException e) {
                    emitter.completeWithError(e);
                }
            });
        }catch (Exception e){
            log.error("Error submitting task to executor", e);
            emitter.completeWithError(e);
        }
    }
    
    private ReactAgent createAgent(){
        DashScopeApi api = DashScopeApi.builder().apiKey(apiKeyConfig.getQwenKey()).build();
        
        DashScopeChatOptions options = DashScopeChatOptions.builder().model(MODEL_NAME).build();
        
        DashScopeChatModel chatModel = DashScopeChatModel.builder().dashScopeApi(api).defaultOptions(options).build();
        
        ReactAgent agent = ReactAgent.builder()
                .name("chat-agent")
                .model(chatModel)
                .methodTools(new TimeTool(), new WeatherSearchTool())
                .saver(new MemorySaver())
                .build();
        
        return agent;
    }
    
    private void sendEvent(SseEmitter emitter, String name, String data){
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IllegalStateException e) {
            // 如果连接已关闭，则不再尝试发送错误信息
            if (e.getMessage().contains("already completed")) {
                log.warn("SSE connection already closed, skipping event: {}", data);
            } else {
                log.error("SSE connection error", e);
            }
        } catch (Exception e) {
            log.error("Error sending SSE event", e);
            try {
                emitter.completeWithError(e);
            } catch (Exception ex) {
                log.error("Failed to complete emitter with error", ex);
            }
        }
    }
    
}
