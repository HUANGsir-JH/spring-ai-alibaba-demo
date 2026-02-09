package org.huang.saademo.service;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.ParallelAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class MultiAgentService {
    
    @Resource
    private ApiKeyConfig apiKeyConfig;
    
    @Resource(name="stramAgentTaskExecutor")
    private ThreadPoolTaskExecutor executor;
    
    private static final String MODEL_NAME = "qwen3-max-2026-01-23";
    
    public record AgentOutput(String outputType, String agentName, String type, String text, Map<String, Object> metadata) {}
    
    private ObjectMapper objectMapper = new ObjectMapper();
    
    private SequentialAgent getSequentialAgent() {
        ChatModel chatModel = buildChatModel();
        
        ReactAgent writeAgent = buildWriteAgent(chatModel);
        ReactAgent reviewAgent = buildReviewAgent(chatModel);
        
        SequentialAgent sequentialAgent = SequentialAgent.builder()
                .name("blog-writing-agent")
                .description("An agent that can write a blog article and review it.")
                .subAgents(List.of(writeAgent, reviewAgent)) // 按照顺序执行子 Agent，先执行 writeAgent，再执行 reviewAgent
                .saver(new MemorySaver())
                .build();
        return sequentialAgent;
    }
    
    /**
     * 按照预先定义的流程顺序调用agent，先调用write-agent写文章，再调用review-agent审核文章，最后由主agent总结输出结果
     * @param userInput 用户输入的写作要求
     */
    public void sequentialAgentCall(String userInput){
        SequentialAgent sequentialAgent = getSequentialAgent();
        
        try {
            Optional<OverAllState> allState = sequentialAgent.invoke(userInput);
            if(allState.isPresent()){
                OverAllState state = allState.get();
                // 复制输出，找个json格式化工具查看会更清晰
                System.out.println("State:\n" + state);
            }else{
                System.out.println("No output");
            }
        } catch (GraphRunnerException e) {
            throw new RuntimeException(e);
        }
    }
    
    // 演示流式输出的内容。
    public void sequentialAgentStreamCall(String userInput, SseEmitter emitter){
        SequentialAgent sequentialAgent = getSequentialAgent();
        
        try{
            executor.submit(()->{
                try {
                    Flux<NodeOutput> stream = sequentialAgent.stream(userInput);
                    stream.subscribe(output->{
                        if(output instanceof StreamingOutput modelOutput){
                            OutputType type = modelOutput.getOutputType();
                            // 如果要区分是哪个Agent的流式输出，可以在构建Agent的时候设置不同的name，然后通过modelOutput.agent()来获取当前输出属于哪个Agent。
                            // 比如这里是subgraph_write-agent和subgraph_review-agent的流式输出，前面加上agentName区分一下。
                            String agentName = modelOutput.agent();
                            Message message = modelOutput.message();
                            if(message!=null){
                                // 这里只是简单地把流式输出的内容发送给前端，实际使用中可以根据 type 和 message 的内容进行更复杂的处理和展示。
                                MessageType messageType = message.getMessageType();
                                Map<String, Object> metadata = message.getMetadata();
                                String text = message.getText();
                                sendEvent(emitter,"[STREAMING_OUTPUT]", buildOutputJson(type.name(),agentName,messageType.getValue(),text,metadata));
                            }
                        }else{
                            sendEvent(emitter,"[NODE_OUTPUT]",output.toString());
                        }
                    },(error)->{
                        log.error("Error in stream execution", error);
                        emitter.completeWithError(error);
                    },()->{
                        log.info("Stream execution completed");
                        emitter.complete();
                    });
                } catch (GraphRunnerException e) {
                    log.error("Error in stream execution", e);
                    emitter.completeWithError(e);
                }
            });
        } catch (Exception e) {
            log.error("Error in sequentialAgentStreamCall", e);
            emitter.completeWithError(e);
        }
        
    }
    
    private ParallelAgent getParallelAgent() {
        ChatModel chatModel = buildChatModel();
        
        ReactAgent writeAgent = buildWriteAgent(chatModel);
        ReactAgent poemAgent = buildPoemAgent(chatModel);
        ReactAgent summaryAgent = buildSummaryAgent(chatModel);
        
        ParallelAgent parallelAgent = ParallelAgent.builder()
                .name("parallel-creator-agent")
                .description("An agent that can write an article and a poem in parallel, then summarize at the same time.")
                .mergeOutputKey("mergedContent")
                .subAgents(List.of(writeAgent, poemAgent, summaryAgent)) // 并行执行子 Agent
                .mergeStrategy(new ParallelAgent.DefaultMergeStrategy()) // 自定义合并策略：实现ParallelAgent.MergeStrategy接口
                .saver(new MemorySaver())
                .build();
        return parallelAgent;
    }
    
    /**
     * 并行调用write-agent和poem-agent，最后由主agent总结输出结果
     * @param userInput 用户输入的写作要求
     */
    public void parallelAgentCall(String userInput){
        ParallelAgent parallelAgent = getParallelAgent();
        
        try {
            Optional<OverAllState> allState = parallelAgent.invoke(userInput);
            if(allState.isPresent()){
                OverAllState state = allState.get();
                // 复制输出，找个json格式化工具查看会更清晰
                System.out.println("State:\n" + state);
            }else {
                System.out.println("No output");
            }
        } catch (GraphRunnerException e) {
            throw new RuntimeException(e);
        }
    }
    
    // 演示流式输出的内容。
    public void parallelAgentStreamCall(String userInput, SseEmitter emitter){
        ParallelAgent parallelAgent = getParallelAgent();
        
        try{
            executor.submit(()->{
                try {
                    Flux<NodeOutput> stream = parallelAgent.stream(userInput);
                    stream.subscribe(output->{
                        if(output instanceof StreamingOutput modelOutput){
                            OutputType type = modelOutput.getOutputType();
                            String agentName = modelOutput.agent();
                            Message message = modelOutput.message();
                            // 并行模式下，多个Agent的流式输出会交错出现，所以需要通过 agentName 来区分是哪个Agent的输出。
                            if(message!=null){
                                MessageType messageType = message.getMessageType();
                                Map<String, Object> metadata = message.getMetadata();
                                String text = message.getText();
                                sendEvent(emitter,"[STREAMING_OUTPUT]", buildOutputJson(type.name(),agentName,messageType.getValue(),text,metadata));
                            }
                        }else{
                            sendEvent(emitter,"[NODE_OUTPUT]",output.toString());
                        }
                    },(error)->{
                        log.error("Error in stream execution", error);
                        emitter.completeWithError(error);
                    },()->{
                        log.info("Stream execution completed");
                        emitter.complete();
                    });
                } catch (GraphRunnerException e) {
                    log.error("Error in stream execution", e);
                    emitter.completeWithError(e);
                }
            });
        } catch (Exception e) {
            log.error("Error in parallelAgentStreamCall", e);
            emitter.completeWithError(e);
        }
    }
    
    private LlmRoutingAgent getLlmRoutingAgent() {
        ChatModel chatModel = buildChatModel();
        
        ReactAgent writeAgent = buildWriteAgent(chatModel);
        ReactAgent poemAgent = buildPoemAgent(chatModel);
        ReactAgent translationAgent = buildTranslationAgent(chatModel);
        
        // 由于是根据大模型来决定路由到哪个子agent，所以子agent的description要写得清晰具体，方便大模型理解和区分它们的能力和职责，从而做出正确的路由决策。
        // 另外，每次请求只能路由到一个子agent执行，子agent执行完直接返回。此外，不支持instruction的占位符（比如{input}）。
        // systemPrompt 用于设置路由决策的系统提示，会替换默认的系统提示。使用 systemPrompt 来定义路由Agent的整体行为和决策框架
        // instruction 用于设置路由决策的用户指令，会作为 UserMessage 添加到消息列表中。使用 instruction 来提供特定场景的路由指导或额外上下文
        LlmRoutingAgent routingAgent = LlmRoutingAgent.builder()
                .name("routing-agent")
                .model(chatModel)
                .systemPrompt("You are a helpful assistant that can route the user input to the most suitable agent based on the content of the input. " +
                        "The available agents are: write-agent, poem-agent, translation-agent. " +
                        "If the input is a writing requirement, route to write-agent. " +
                        "If the input is a poetry writing requirement, route to poem-agent. " +
                        "If the input is a translation requirement, route to translation-agent.")
                .instruction("Please analyze the user input and decide which agent is the most suitable to handle it. " +
                        "Only choose one agent for each input. " +
                        "If the input does not match any agent's capabilities, you can choose not to route to any agent.")
                .description("An agent that can route the input to different agents based on the content.")
                .subAgents(List.of(writeAgent, poemAgent, translationAgent))
                .saver(new MemorySaver())
                .build();
        return routingAgent;
    }
    
    /**
     * 根据用户输入的内容，由大模型智能地路由到不同的agent处理。比如用户输入的是写作要求，就路由到write-agent；如果用户输入的是翻译要求，就路由到translation-agent。
     * @param userInput
     */
    public void llmRoutingAgentCall(String userInput){
        LlmRoutingAgent routingAgent = getLlmRoutingAgent();
        
        try {
            Optional<OverAllState> allState = routingAgent.invoke(userInput);
            if(allState.isPresent()){
                OverAllState state = allState.get();
                // 复制输出，找个json格式化工具查看会更清晰
                System.out.println("State:\n" + state);
            }else {
                System.out.println("No output");
            }
        } catch (GraphRunnerException e) {
            throw new RuntimeException(e);
        }
    }
    
    public void llmRoutingAgentStreamCall(String userInput, SseEmitter emitter){
        LlmRoutingAgent routingAgent = getLlmRoutingAgent();
        
        try{
            executor.submit(()->{
                try {
                    Flux<NodeOutput> stream = routingAgent.stream(userInput);
                    stream.subscribe(output->{
                        if(output instanceof StreamingOutput modelOutput){
                            OutputType type = modelOutput.getOutputType();
                            String agentName = modelOutput.agent();
                            Message message = modelOutput.message();
                            // 路由模式下，只有被路由到的子Agent会有流式输出，观察到的agentName就是被路由到的那个Agent。
                            if(message!=null){
                                MessageType messageType = message.getMessageType();
                                Map<String, Object> metadata = message.getMetadata();
                                String text = message.getText();
                                sendEvent(emitter,"[STREAMING_OUTPUT]", buildOutputJson(type.name(),agentName,messageType.getValue(),text,metadata));
                            }
                        }else{
                            sendEvent(emitter,"[NODE_OUTPUT]",output.toString());
                        }
                    },(error)->{
                        log.error("Error in stream execution", error);
                        emitter.completeWithError(error);
                    },()->{
                        log.info("Stream execution completed");
                        emitter.complete();
                    });
                } catch (GraphRunnerException e) {
                    log.error("Error in stream execution", e);
                    emitter.completeWithError(e);
                }
            });
        } catch (Exception e) {
            log.error("Error in llmRoutingAgentStreamCall", e);
            emitter.completeWithError(e);
        }
    }
    
    private SupervisorAgent getSupervisorAgent() {
        ChatModel chatModel = buildChatModel();
        
        ReactAgent writeAgent = buildWriteAgent(chatModel);
        ReactAgent translationAgent = buildTranslationAgent(chatModel);
        ReactAgent mainAgent = buildMainAgent(chatModel);
        
        // 和llmRoutingAgent不同，SupervisorAgent会同时调用多个子Agent。
        // 子Agent执行完成后会返回监督者，监督者可以继续路由到其他Agent，实现多步骤任务处理
        // Instruction占位符支持：instruction 支持使用占位符（如 {article_content}）读取前序Agent的输出
        // 自动重试机制：内置重试机制（最多2次），确保路由决策的可靠性
        // 任务完成控制：监督者可以返回 FINISH 来结束任务流程
        // 嵌套使用：可以将 SupervisorAgent 作为 SequentialAgent 的子Agent，实现更复杂的工作流
        
        // ps：就目前的稳定想来看，个人感觉SupervisorAgent的表现可能不如预期，尤其是在复杂任务和多轮路由场景下，可能会出现路由错误或者无法正确结束任务的情况。
        SupervisorAgent supervisorAgent = SupervisorAgent.builder()
                .name("supervisor-agent")
                .model(chatModel)
                .description("An agent that can supervise the execution of other agents and give feedback.")
                .mainAgent(mainAgent) // 版本更新后，使用mainAgent来做路由决策。
                .subAgents(List.of(writeAgent, translationAgent))
                .saver(new MemorySaver())
                .build();
        return supervisorAgent;
    }
    
    /**
     * 监督者Agent可以同时监控多个子Agent的执行，并且在子Agent执行完成后对它们的输出进行评审和反馈。比如在写文章的场景中，监督者Agent可以同时监控write-agent和translation-agent的执行，在它们完成后对写的文章和翻译的结果进行评审，给出改进建议或者指出错误。
     * @param userInput 用户输入的内容，可能包含写作要求和翻译要求
     */
    public void supervisorAgentCall(String userInput){
        SupervisorAgent supervisorAgent = getSupervisorAgent();
        
        try {
            Optional<OverAllState> allState = supervisorAgent.invoke(userInput);
            if(allState.isPresent()){
                OverAllState state = allState.get();
                // 复制输出，找个json格式化工具查看会更清晰
                System.out.println("State:\n" + state);
            }else {
                System.out.println("No output");
            }
        } catch (GraphRunnerException e) {
            throw new RuntimeException(e);
        }
        
    }
    
    public void supervisorAgentStreamCall(String userInput, SseEmitter emitter){
        SupervisorAgent supervisorAgent = getSupervisorAgent();
        
        try{
            executor.submit(()->{
                try {
                    Flux<NodeOutput> stream = supervisorAgent.stream(userInput);
                    stream.subscribe(output->{
                        if(output instanceof StreamingOutput modelOutput){
                            OutputType type = modelOutput.getOutputType();
                            String agentName = modelOutput.agent();
                            Message message = modelOutput.message();
                            // 注意观察agentName，监管者模式下，agentName是监管者的名字，而不是子Agent的名字。
                            // 比如这里是"supervisor-agent"，而不是"subgraph_write-agent"或者"subgraph_translation-agent"。
                            if(message!=null){
                                MessageType messageType = message.getMessageType();
                                Map<String, Object> metadata = message.getMetadata();
                                String text = message.getText();
                                // ps：这里似乎由于AI输出的字符会导致json解析出错，但是内容却接收到了。
                                // todo 已经提交issue，后续看官方回复。
                                sendEvent(emitter,"[STREAMING_OUTPUT]", buildOutputJson(type.name(),agentName,messageType.getValue(),text,metadata));
                            }
                        }else{
                            sendEvent(emitter,"[NODE_OUTPUT]",output.toString());
                        }
                    },(error)->{
                        log.error("Error in stream execution", error);
                        emitter.completeWithError(error);
                    },()->{
                        log.info("Stream execution completed");
                        emitter.complete();
                    });
                } catch (GraphRunnerException e) {
                    log.error("Error in stream execution", e);
                    emitter.completeWithError(e);
                }
            });
        } catch (Exception e) {
            log.error("Error in supervisorAgentStreamCall", e);
            emitter.completeWithError(e);
        }
    }
    
    private ChatModel buildChatModel(){
        DashScopeApi api = DashScopeApi.builder().apiKey(apiKeyConfig.getQwenKey()).build();
        
        DashScopeChatOptions options = DashScopeChatOptions.builder().model(MODEL_NAME).build();
        
        return DashScopeChatModel.builder().dashScopeApi(api).defaultOptions(options).build();
    }
    
    private ReactAgent buildWriteAgent(ChatModel chatModel){
        return ReactAgent.builder().name("write-agent")
                .description("An agent that can write an article based on user input")
                .instruction("You are a helpful assistant that can write articles based on user input: {input}")
                .outputKey("article")
                .model(chatModel)
//                .returnReasoningContents(true)
                // 控制子 Agent 的上下文是否返回父流程中。如果设置为 false，则其他 Agent 不会有机会看到这个子 Agent 内部的推理过程，它们只能看到这个 Agent 输出的内容（比如通过 outputKey 引用）。这对于减少上下文大小、提高效率非常有用。默认为false
//                .includeContents(false)
                // 父流程中可能包含非常多子 Agent 的推理过程、每个子 Agent 的输出等。includeContents 用来控制当前子 Agent 执行时，是只基于自己的 instruction 给到的内容工作，还是会带上所有父流程的上下文。设置为 false 可以让子 Agent 专注于自己的任务，不受父流程复杂上下文的影响。默认为true
                .saver(new MemorySaver())
                .build();
    }
    
    private ReactAgent buildPoemAgent(ChatModel chatModel){
        return ReactAgent.builder().name("poem-agent")
                .description("An agent that can write a poem based on user input")
                .instruction("You are a poet that can write a poem based on user input: {input}")
                .outputKey("poem")
                .model(chatModel)
                .saver(new MemorySaver())
                .build();
    }
    
    private ReactAgent buildSummaryAgent(ChatModel chatModel){
        return ReactAgent.builder().name("summary-agent")
                .description("An agent that can summarize the content")
                .instruction("You are a helpful assistant that can summarize a topic based on user input: {input}")
                .outputKey("summary")
                .model(chatModel)
                .saver(new MemorySaver())
                .build();
    }
    
    private ReactAgent buildReviewAgent(ChatModel chatModel){
        return ReactAgent.builder().name("review-agent")
                .description("An agent that can review the article written by others and give feedback.")
                .instruction("You are a strict reviewer that can review the article written by write-agent and give feedback. The article is: {article}")
                .outputKey("feedback")
                .model(chatModel)
                .saver(new MemorySaver())
                .build();
    }
    
    private ReactAgent buildTranslationAgent(ChatModel chatModel){
        return ReactAgent.builder().name("translation-agent")
                .description("An agent that can translate the input into English.")
                .instruction("You are a helpful assistant that can translate the input into English.")
                .outputKey("translation")
                .model(chatModel)
                .saver(new MemorySaver())
                .build();
    }
    
    private ReactAgent buildMainAgent(ChatModel chatModel){
        return ReactAgent.builder().name("main-agent")
                .description("The main agent that can route to different agents based on the input.")
                .instruction("用户的请求：{input}")
                // 需要自己在系统提示词定义好mainAgent的输出格式，确保可以进行正确的路由决策
                // 参见issue：https://github.com/alibaba/spring-ai-alibaba/issues/4266
                .systemPrompt("""
                        你是一个智能的内容处理监督者，需要根据用户的输入内容，智能地路由到最合适的子Agent去处理，并且可以多轮路由，直到任务完成。
                        可用的子Agent：write-agent（写作）、translation-agent（翻译）
                        ## 路由决策输出格式（仅在选择子Agent时适用）
                        当且仅当需要做出路由决策（选择下一个要调用的子Agent或结束任务）时，请以 JSON 数组格式输出，供系统解析路由；此格式仅用于本次路由，不影响你在其他场景下的主要任务输出格式。
                        - 选择单个子Agent 时输出: ["write-agent"] 或 ["translation-agent"]
                        - 选择多个子Agent 并行时输出: ["write-agent", "translation-agent"]
                        - 任务全部完成时输出: [] 或 ["FINISH"]
                        合法元素仅限: write-agent、translation-agent、FINISH。做路由决策时只输出上述 JSON 数组，不要包含其他解释
                        """)
                .model(chatModel)
                .saver(new MemorySaver())
                .build();
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
        AgentOutput agentOutput = new AgentOutput(outputType, agentName,type, text, metadata);
        try {
            return objectMapper.writeValueAsString(agentOutput);
        } catch (JsonProcessingException e) {
            log.error("Error converting AgentOutput to JSON", e);
            return "{}"; // 返回一个空的JSON对象作为默认值
        }
    }
}
