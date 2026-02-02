package org.huang.saademo.service;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.huang.saademo.config.ApiKeyConfig;
import org.huang.saademo.hook.TimeRecordAgentHook;
import org.huang.saademo.interceptor.TimeRecordModelInterceptor;
import org.huang.saademo.interceptor.ToolRecordInterceptor;
import org.huang.saademo.tools.TimeTool;
import org.huang.saademo.tools.WeatherSearchTool;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;


@Service
@Slf4j
public class StreamMemService {
    
    @Resource
    private ApiKeyConfig apiKeyConfig;
    
    @Resource(name="stramAgentTaskExecutor")
    private ThreadPoolTaskExecutor executor;
    
    private static final String MODEL_NAME = "qwen3-max-2026-01-23";
    
    @Resource(name="redissonClient")
    private RedissonClient redissonClient;
    
    @Resource
    private TimeRecordAgentHook timeRecordAgentHook;
    
    @Resource
    private TimeRecordModelInterceptor timeRecordModelInterceptor;
    
    @Resource
    private ToolRecordInterceptor toolRecordInterceptor;
    
    public void streamCall(SseEmitter emitter, String prompt) {
        ReactAgent agent = createAgent();
        
        RunnableConfig config = RunnableConfig.builder()
                .threadId("thread-1")
                .addMetadata("user_id", "hjh")
                .build();
        
        try{
            executor.submit(()->{
                try{
                    Flux<NodeOutput> stream = agent.stream(prompt, config);
                    stream.subscribe(output ->{
                        if(output instanceof StreamingOutput modelResponse){
                            OutputType type = modelResponse.getOutputType();
                            Message message = modelResponse.message();
                            
                            switch (type){
                                case AGENT_MODEL_STREAMING -> sendEvent(emitter, "[MODEL]", message.getText());
                                case AGENT_MODEL_FINISHED -> {
                                    sendEvent(emitter, "[Done]", "Agent processing completed.");
                                    emitter.complete();
                                }
                                case AGENT_TOOL_STREAMING -> log.info("Tool streaming: {}", message.toString());
                                case AGENT_TOOL_FINISHED -> {
                                    if(message instanceof ToolResponseMessage tool){
                                        tool.getResponses().forEach(response->{
                                            String toolOutput = "id: "+response.id()+", name: "+response.name()+", data: "+ response.responseData();
                                            sendEvent(emitter, "[TOOL]", toolOutput);
                                        });
                                    }
                                }
                            }
                        }
                    }, error ->{
                        log.error("Error in streaming: ", error);
                        sendEvent(emitter, "[Error]", "An error occurred: " + error.getMessage());
                        emitter.completeWithError(error);
                    }, ()->{
                        sendEvent(emitter, "[Complete]", "AI Streaming completed.");
                        emitter.complete();
                    });
                }catch (Exception e){
                    log.error("Error during streaming call", e);
                    sendEvent(emitter, "[Error]", "An error occurred: " + e.getMessage());
                    emitter.completeWithError(e);
                }
            });
        } catch (Exception e) {
            log.error("Error submitting task to executor", e);
            sendEvent(emitter, "[Error]", "An error occurred: " + e.getMessage());
            emitter.completeWithError(e);
        }
        
    }
    
    private ReactAgent createAgent(){
        DashScopeApi api = DashScopeApi.builder().apiKey(apiKeyConfig.getQwenKey()).build();
        
        DashScopeChatOptions options = DashScopeChatOptions.builder().model(MODEL_NAME).build();
        
        DashScopeChatModel chatModel = DashScopeChatModel.builder().dashScopeApi(api).defaultOptions(options).build();
        
        // 使用RedisSaver作为存储器
        RedisSaver redisSaver = RedisSaver.builder()
                .redisson(redissonClient).build();
        
        ReactAgent agent = ReactAgent.builder()
                .name("chat-agent")
                .model(chatModel)
                .hooks(timeRecordAgentHook)
                .systemPrompt("你是一个乐于助人的智能助理，请根据用户的提问提供准确且有帮助的回答。")
                .interceptors(timeRecordModelInterceptor,toolRecordInterceptor)
                .methodTools(new TimeTool(), new WeatherSearchTool())
                .saver(redisSaver)
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
