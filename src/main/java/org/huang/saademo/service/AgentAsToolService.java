package org.huang.saademo.service;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.agent.AgentTool;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.huang.saademo.config.ApiKeyConfig;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.Map;

@Service
@Slf4j
public class AgentAsToolService {
    
    @Resource
    private ApiKeyConfig apiKeyConfig;
    
    @Resource(name="stramAgentTaskExecutor")
    private ThreadPoolTaskExecutor executor;
    
    private static final String MODEL_NAME = "qwen3-max-2026-01-23";
    
    public record AgentOutput(String outputType, String agentName, String type, String text, Map<String, Object> metadata) {}
    
    private ObjectMapper objectMapper = new ObjectMapper();
    
    public void multiToolAgentCall(SseEmitter emitter, String input){
        ReactAgent agent = buildMultiToolAgent();
        
        try{
            executor.submit(()->{
                try{
                    Flux<NodeOutput> stream = agent.stream(input);
                    stream.subscribe(
                            output -> {
                                if(output instanceof StreamingOutput modelRes){
                                    OutputType outputType = modelRes.getOutputType();
                                    Message message = modelRes.message();
                                    String agentName = modelRes.agent();
                                    if(message!=null){
                                        MessageType messageType = message.getMessageType();
                                        Map<String, Object> metadata = message.getMetadata();
                                        String text = message.getText();
                                        sendEvent(emitter,"[Stream-Output]", buildOutputJson("Stream-Output", agentName, messageType.name(), text, metadata));
                                    }
                                }else{
                                    sendEvent(emitter,"[Node-Output]", output.toString());
                                }
                            },
                            error -> {
                                log.error("Agent stream error", error);
                                emitter.completeWithError(error);
                            },
                            () -> {
                                sendEvent(emitter,"[Stream-Complete]", "Agent task completed");
                                emitter.complete();
                            }
                    );
                }catch (Exception e){
                    log.error("Agent call failed", e);
                    emitter.completeWithError(e);
                }
            });
        } catch (Exception e) {
            log.error("Submit agent task failed", e);
            emitter.completeWithError(e);
        }
    }
    
    
    private ChatModel buildChatModel(){
        DashScopeApi api = DashScopeApi.builder().apiKey(apiKeyConfig.getQwenKey()).build();
        
        DashScopeChatOptions options = DashScopeChatOptions.builder().model(MODEL_NAME).build();
        
        return DashScopeChatModel.builder().dashScopeApi(api).defaultOptions(options).build();
    }
    
    private ReactAgent buildMultiToolAgent(){
        ChatModel chatModel = buildChatModel();
        
        ReactAgent writeAgent = buildWriteAgent();
        ReactAgent translateAgent = buildTranslateAgent();
        
        ReactAgent multiToolAgent = ReactAgent.builder()
                .name("multi-tool-agent")
                .description("这是一个多工具代理，包含写作工具和翻译工具。根据用户的输入内容，选择合适的工具进行处理。")
                .instruction("你是一个多工具代理，用户会给你一个输入内容，你需要根据这个内容选择合适的工具进行处理。" +
                        "如果用户的输入内容是关于写作需求的，你需要使用写作工具生成文章内容；" +
                        "如果用户的输入内容是关于翻译需求的，你需要使用翻译工具进行翻译。")
                .model(chatModel)
                .tools(
                        AgentTool.getFunctionToolCallback(writeAgent),
                        AgentTool.getFunctionToolCallback(translateAgent)
                )
                .build();
        
        return multiToolAgent;
    }
    
    
    private record writeAgentInput(
            String input,
            int wordCount,
            String style
    ){}
    private record writeAgentOutput(
            String title,
            String content
    ){}
    private ReactAgent buildWriteAgent(){
        ChatModel chatModel = buildChatModel();
        
        ReactAgent writeAgent = ReactAgent.builder()
                .name("write-agent")
                .description("这是一个写作工具，输入是用户的写作需求，输出是根据需求生成的文章内容。")
                .instruction("你是一个写作工具，用户会给你一个写作需求，你需要根据这个需求生成一段文章内容。对于没有明确写作需求的输入，你需要根据输入内容进行合理的推断，生成符合用户需求的文章内容。")
                .inputType(writeAgentInput.class) // 指定输入类型为writeAgentInput
                .outputType(writeAgentOutput.class) // 指定输出类型为writeAgentOutput
                .model(chatModel)
                .build();
        
        return writeAgent;
    }
    
    private record translateAgentInput(
            String text,
            String targetLanguage
    ){}
    private record translateAgentOutput(
            String translatedText
    ){}
    
    private ReactAgent buildTranslateAgent(){
        ChatModel chatModel = buildChatModel();
        
        ReactAgent translateAgent = ReactAgent.builder()
                .name("translate-agent")
                .description("这是一个翻译工具，输入是用户提供的文本内容，输出是将该文本内容翻译成指定语言后的结果。")
                .instruction("你是一个翻译工具，用户会给你一段文本内容和目标语言，你需要将该文本内容翻译成目标语言。对于没有明确目标语言的输入，你需要根据输入内容进行合理的推断，生成符合用户需求的翻译结果。")
                .inputType(translateAgentInput.class) // 指定输入类型为translateAgentInput
                .outputType(translateAgentOutput.class) // 指定输出类型为translateAgentOutput
                .model(chatModel)
                .build();
        
        return translateAgent;
    }
    
    private void sendEvent(SseEmitter emitter, String eventName, Object data){
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (Exception e) {
            log.error("Error sending SSE event", e);
            emitter.completeWithError(e);
        }
    }
    
    private String buildOutputJson(String outputType, String agentName, String type,  String text, Map<String, Object> metadata) {
        MultiAgentService.AgentOutput agentOutput = new MultiAgentService.AgentOutput(outputType, agentName,type, text, metadata);
        try {
            return objectMapper.writeValueAsString(agentOutput);
        } catch (JsonProcessingException e) {
            log.error("Error converting AgentOutput to JSON", e);
            return "{}"; // 返回一个空的JSON对象作为默认值
        }
    }
}
