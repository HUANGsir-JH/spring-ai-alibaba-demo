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
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import jakarta.annotation.Resource;
import org.huang.saademo.config.ApiKeyConfig;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

@Service
public class MultiAgentService {
    
    @Resource
    private ApiKeyConfig apiKeyConfig;
    
    private static final String MODEL_NAME = "qwen3-max-2026-01-23";
    
    /**
     * 按照预先定义的流程顺序调用agent，先调用write-agent写文章，再调用review-agent审核文章，最后由主agent总结输出结果
     * @param userInput 用户输入的写作要求
     */
    public void sequentialAgentCall(String userInput){
        ChatModel chatModel = buildChatModel();
        
        ReactAgent writeAgent = buildWriteAgent(chatModel);
        ReactAgent reviewAgent = buildReviewAgent(chatModel);
        
        SequentialAgent sequentialAgent = SequentialAgent.builder()
                .name("blog-writing-agent")
                .description("An agent that can write a blog article and review it.")
                .subAgents(List.of(writeAgent, reviewAgent)) // 按照顺序执行子 Agent，先执行 writeAgent，再执行 reviewAgent
                .saver(new MemorySaver())
                .build();
        
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
    
    /**
     * 并行调用write-agent和poem-agent，最后由主agent总结输出结果
     * @param userInput 用户输入的写作要求
     */
    public void parallelAgentCall(String userInput){
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
    
    /**
     * 根据用户输入的内容，由大模型智能地路由到不同的agent处理。比如用户输入的是写作要求，就路由到write-agent；如果用户输入的是翻译要求，就路由到translation-agent。
     * @param userInput
     */
    public void llmRoutingAgentCall(String userInput){
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
    
    /**
     * 监督者Agent可以同时监控多个子Agent的执行，并且在子Agent执行完成后对它们的输出进行评审和反馈。比如在写文章的场景中，监督者Agent可以同时监控write-agent和translation-agent的执行，在它们完成后对写的文章和翻译的结果进行评审，给出改进建议或者指出错误。
     * @param userInput 用户输入的内容，可能包含写作要求和翻译要求
     */
    public void supervisorAgentCall(String userInput){
        ChatModel chatModel = buildChatModel();
        
        ReactAgent writeAgent = buildWriteAgent(chatModel);
        ReactAgent translationAgent = buildTranslationAgent(chatModel);
        
        String systemPrompt = "You are a helpful assistant that can write an article based on user input and translate the input into English. " +
                "The available agents are: write-agent, translation-agent. " +
                "If the input is a writing requirement, route to write-agent. " +
                "If the input is a translation requirement, route to translation-agent.";
        String instruction = "You are a supervisor agent that can oversee the execution of write-agent and translation-agent. " +
                "You can get the output of the sub agents and give feedback on their performance. " +
                "Now, you receive the content from the write-agent: {article}, and the content from the translation-agent: {translation}. Please review their outputs and give feedback if there is any mistake or improvement suggestion.";
        
        // 和llmRoutingAgent不同，SupervisorAgent会同时调用多个子Agent。
        // 子Agent执行完成后会返回监督者，监督者可以继续路由到其他Agent，实现多步骤任务处理
        // Instruction占位符支持：instruction 支持使用占位符（如 {article_content}）读取前序Agent的输出
        // 自动重试机制：内置重试机制（最多2次），确保路由决策的可靠性
        // 任务完成控制：监督者可以返回 FINISH 来结束任务流程
        // 嵌套使用：可以将 SupervisorAgent 作为 SequentialAgent 的子Agent，实现更复杂的工作流
        SupervisorAgent supervisorAgent = SupervisorAgent.builder()
                .name("supervisor-agent")
                .model(chatModel)
                .systemPrompt(systemPrompt)
                .instruction(instruction)
                .description("An agent that can supervise the execution of other agents and give feedback.")
                .mainAgent(writeAgent) // 官方文档没写mainAgent，但是启动报错了，需要指定一个主Agent。
                .subAgents(List.of(writeAgent, translationAgent))
                .saver(new MemorySaver())
                .build();
        
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
}
