package org.huang.saademo.service;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import com.alibaba.cloud.ai.graph.agent.tools.ShellTool;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.huang.saademo.common.Constants;
import org.huang.saademo.config.ApiKeyConfig;
import org.huang.saademo.hook.MessageManageHook;
import org.huang.saademo.hook.TimeRecordAgentHook;
import org.huang.saademo.interceptor.TimeRecordModelInterceptor;
import org.huang.saademo.interceptor.ToolRecordInterceptor;
import org.huang.saademo.manager.InterruptMetadataManager;
import org.huang.saademo.manager.SSEManager;
import org.huang.saademo.tools.TimeTool;
import org.huang.saademo.tools.WeatherSearchTool;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;


@Service
@Slf4j
public class StreamMemService {
    
    @Resource
    private ApiKeyConfig apiKeyConfig;
    
    @Resource(name="stramAgentTaskExecutor")
    private ThreadPoolTaskExecutor executor;
    
    @Resource
    private SSEManager sseManager;
    
    private static final String MODEL_NAME = "qwen3-max-2026-01-23";
    
    @Resource(name="redissonClient")
    private RedissonClient redissonClient;
    
    @Resource
    private TimeRecordAgentHook timeRecordAgentHook;
    
    @Resource
    private TimeRecordModelInterceptor timeRecordModelInterceptor;
    
    @Resource
    private ToolRecordInterceptor toolRecordInterceptor;
    
    @Resource
    private MessageManageHook messageManageHook;
    
    @Resource
    private InterruptMetadataManager metadataManager;
    
    public void streamCall(String prompt, String sessionId, Integer humanResponse) {
        ReactAgent agent = createAgent();
        
        InterruptionMetadata humanDecision = null;
        
        if(humanResponse!=null){
            InterruptionMetadata metadata = metadataManager.get(sessionId);
            if(humanResponse.equals(Constants.TOOL_APPROVE)){
                humanDecision = approveAll(metadata);
            }else if(humanResponse.equals(Constants.TOOL_EDIT)){
                // todo 编辑功能需要前端提供编辑界面，用户编辑后将修改后的结果传回后端，这个过程比较复杂，后续再完善，当前仅传递edit这个状态。
                // todo 目前标记edit状态会当作批准处理，应该算作bug，需要后续完善
                humanDecision = edit(metadata);
            }else if(humanResponse.equals(Constants.TOOL_REJECT)) {
                humanDecision = rejectAll(metadata);
            }
            metadataManager.remove(sessionId); // 处理完毕后移除metadata，避免内存泄漏
        }
        
        RunnableConfig.Builder configBuilder = RunnableConfig.builder()
                .threadId(sessionId)
                .addMetadata("user_id", "hjh");
        
        if(humanDecision!=null){
            configBuilder.addMetadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY, humanDecision);
        }
        
        SseEmitter emitter = sseManager.getEmitter(sessionId);
        
        try{
            executor.submit(()->{
                try{
                    Flux<NodeOutput> stream = agent.stream(prompt, configBuilder.build());
                    stream.subscribe(output ->{
                        if(output instanceof StreamingOutput modelResponse){
                            OutputType type = modelResponse.getOutputType();
                            Message message = modelResponse.message();
                            
                            switch (type){
                                case AGENT_MODEL_STREAMING -> {
                                    Object thinkContent = message.getMetadata().get("reasoningContent");
                                    if(thinkContent!=null && !thinkContent.toString().isEmpty()){ // 有思考内容
                                        // 对思考内容进行JSON安全处理
                                        sseManager.sendEvent(emitter, sessionId, Constants.SSE_EVENT_THINKING, thinkContent.toString());
                                    }else{ // 纯模型输出
                                        sseManager.sendEvent(emitter, sessionId, Constants.SSE_EVENT_MODEL, message.getText());
                                    }
                                }
                                case AGENT_TOOL_STREAMING -> log.info("Tool streaming: {}", message.toString());
                                case AGENT_TOOL_FINISHED -> {
                                    if(message instanceof ToolResponseMessage tool){
                                        tool.getResponses().forEach(response->{
                                            String toolOutput = "id: "+response.id()+", name: "+response.name()+", data: "+ response.responseData();
                                            // 对工具响应数据进行JSON安全处理
                                            sseManager.sendEvent(emitter, sessionId, Constants.SSE_EVENT_TOOL, toolOutput);
                                        });
                                    }
                                }
                                default -> {
                                    String messageText = message == null ? "[No Text]" : message.getText();
                                    // 对默认消息进行JSON安全处理
                                    log.info("Other streaming type: {}, message: {}", type, messageText);
                                }
                            }
                        }else if(output instanceof InterruptionMetadata metadata){
                            List<InterruptionMetadata.ToolFeedback> toolFeedbacks = metadata.toolFeedbacks();
                            toolFeedbacks.forEach(feedback->{
                                String info = "[Tool]: " + feedback.getName() + ", [Id]: " + feedback.getId() + ", [Arguments]: "
                                        + feedback.getArguments() + ", [Description]: " + feedback.getDescription() + ", [Result]: "
                                        + feedback.getResult();
                                sseManager.sendEvent(emitter, sessionId, Constants.SSE_EVENT_INTERRUPT, info);
                            });
                            metadataManager.put(sessionId, metadata); // 存储中断元数据，等待前端批准后使用
                        }
                    }, error ->{
                        log.error("Error in streaming: ", error);
                        // 对错误信息进行JSON安全处理
                        sseManager.sendEvent(emitter, sessionId, Constants.SSE_EVENT_ERROR, error.getMessage());
                        emitter.completeWithError(error);
                    }, ()->{
                        sseManager.sendEvent(emitter, sessionId, Constants.SSE_EVENT_COMPLETE, "Stream completed");
                        emitter.complete();
                    });
                }catch (Exception e){
                    log.error("Error during streaming call", e);
                    // 对异常信息进行JSON安全处理
                    sseManager.sendEvent(emitter, sessionId, Constants.SSE_EVENT_ERROR, e.getMessage());
                    emitter.completeWithError(e);
                }
            });
        } catch (Exception e) {
            log.error("Error submitting task to executor", e);
            // 对提交错误信息进行JSON安全处理
            sseManager.sendEvent(emitter, sessionId, Constants.SSE_EVENT_ERROR, e.getMessage());
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
        
        // 配置Human-in-the-loop Hook，当调用getCurrentTime工具时需要人工批准
        // 我咋感觉这功能那么难用呢？如果要用户介入处理，就需要在前端展示一个批准界面，用户批准后再把批准结果传回后端，这个过程中还要维护好metadata的状态，不然就很麻烦了
        HumanInTheLoopHook human = HumanInTheLoopHook.builder().approvalOn("getCurrentTime",
                ToolConfig.builder()
                        .description("Get the current time need human approval")
                        .build()).build();
        
        ReactAgent agent = ReactAgent.builder()
                .name("chat-agent")
                .model(chatModel)
                .hooks(timeRecordAgentHook, messageManageHook, human)
                .systemPrompt(genSystemPrompt())
                .interceptors(timeRecordModelInterceptor,toolRecordInterceptor)
                .methodTools(new TimeTool(), new WeatherSearchTool())
                .saver(redisSaver)
                .build();
        
        return agent;
    }
    
    private String genSystemPrompt(){
        return """
                你是一个智能助手，你的任务是根据用户的输入提供有用的信息和帮助。
                你可以使用工具来获取信息或执行任务，但在使用某些工具之前需要获得用户的批准。
                如果用户批准了工具的使用，请使用该工具并提供结果；
                如果用户拒绝了工具的使用，询问用户是否需要编辑工具的参数或是进行其他操作；
                """;
    }
    
    private InterruptionMetadata approveAll(InterruptionMetadata metadata){
        InterruptionMetadata.Builder builder = InterruptionMetadata.builder().nodeId(metadata.node()).state(metadata.state());
        
        metadata.toolFeedbacks().forEach(feedback->{
            builder.addToolFeedback(
                    InterruptionMetadata.ToolFeedback.builder(feedback)
                            .result(InterruptionMetadata.ToolFeedback.FeedbackResult.APPROVED)
                            .build()
            );
        });
        
        return builder.build();
    }
    
    private InterruptionMetadata rejectAll(InterruptionMetadata metadata){
        InterruptionMetadata.Builder builder = InterruptionMetadata.builder().nodeId(metadata.node()).state(metadata.state());
        
        metadata.toolFeedbacks().forEach(feedback->{
            builder.addToolFeedback(
                    InterruptionMetadata.ToolFeedback.builder(feedback)
                            .result(InterruptionMetadata.ToolFeedback.FeedbackResult.REJECTED)
                            .build()
            );
        });
        
        return builder.build();
    }
    
    private InterruptionMetadata edit(InterruptionMetadata metadata){
        InterruptionMetadata.Builder builder = InterruptionMetadata.builder().nodeId(metadata.node()).state(metadata.state());
        
        metadata.toolFeedbacks().forEach(feedback->{
            builder.addToolFeedback(
                    InterruptionMetadata.ToolFeedback.builder(feedback)
                            .result(InterruptionMetadata.ToolFeedback.FeedbackResult.EDITED)
                            .build()
            );
        });
        
        return builder.build();
    }
    
    private InterruptionMetadata edit(InterruptionMetadata metadata, String toolName, String newArguments){
        InterruptionMetadata.Builder builder = InterruptionMetadata.builder().nodeId(metadata.node()).state(metadata.state());
        
        metadata.toolFeedbacks().forEach(feedback->{
            if(feedback.getName().equals(toolName)){
                builder.addToolFeedback(
                        InterruptionMetadata.ToolFeedback.builder(feedback)
                                .arguments(newArguments)
                                .result(InterruptionMetadata.ToolFeedback.FeedbackResult.EDITED)
                                .build()
                );
            }else{
                builder.addToolFeedback(
                        InterruptionMetadata.ToolFeedback.builder(feedback)
                                .result(InterruptionMetadata.ToolFeedback.FeedbackResult.REJECTED)
                                .build()
                );
            }
        });
        
        return builder.build();
    }
    
}