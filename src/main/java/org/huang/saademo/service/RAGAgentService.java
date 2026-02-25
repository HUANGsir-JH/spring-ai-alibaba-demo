package org.huang.saademo.service;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.huang.saademo.config.ApiKeyConfig;
import org.huang.saademo.hook.RAGHook;
import org.huang.saademo.tools.RAGTool;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pinecone.PineconeVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class RAGAgentService {
    @Resource
    private ApiKeyConfig apiKeyConfig;
    
    @Resource(name= "streamAgentTaskExecutor")
    private ThreadPoolTaskExecutor executor;
    
    @Resource(name = "customPineconeVectorStore")
    private PineconeVectorStore vectorStore;
    
    @Resource
    private RAGHook ragHook;
    
    @Resource
    private RAGTool ragTool;
    
    private static final String MODEL_NAME = "qwen3-max-2026-01-23";
    
    public record AgentOutput(String outputType, String agentName, String type, String text, Map<String, Object> metadata) {}
    
    private ObjectMapper objectMapper = new ObjectMapper();
    
    public OverAllState agentCall(String userInput){
        ReactAgent agent = buildRAGAgent();
        try {
            Optional<OverAllState> allState = agent.invoke(userInput);
            if(allState.isPresent()){
                return allState.get();
            } else {
                log.warn("Agent execution completed but no output was produced.");
                return null;
            }
        } catch (GraphRunnerException e) {
            log.error("Error during agent execution: ", e);
            throw new RuntimeException(e);
        }
    }
    
    private ReactAgent buildRAGAgent(){
        return ReactAgent.builder()
                .name("RAGAgent")
                .description("一个基于ReactAgent的RAG智能体，能够根据用户输入的问题，调用检索工具从向量数据库中获取相关内容，并结合大模型进行回答")
                .instruction("你是一个智能助手。当需要查找信息时，使用工具。基于检索到的信息回答用户的问题，并引用相关片段。")
                .model(buildChatModel())
                .hooks(ragHook)
                .methodTools(ragTool)
                .build();
    }
    
    private ChatModel buildChatModel(){
        DashScopeApi api = DashScopeApi.builder().apiKey(apiKeyConfig.getQwenKey()).build();
        
        DashScopeChatOptions options =
                DashScopeChatOptions.builder().model(MODEL_NAME).build();
        
        return DashScopeChatModel.builder().dashScopeApi(api).defaultOptions(options).build();
    }
    
    /**
     * 将markdown文档中的内容添加到向量数据库中，一次性添加，后续可以增加增量添加的功能
     */
    public void addMarkdownToVectorDB(){
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        // 使用通配符获取 rags 目录下所有 md 文件
        try {
            org.springframework.core.io.Resource[] resources = resolver.getResources("classpath:rags/*.md");
            MarkdownDocumentReader documentReader = new MarkdownDocumentReader(List.of(resources), MarkdownDocumentReaderConfig.defaultConfig());
            List<Document> documents = documentReader.get();
            // 阿里云的v4版本嵌入模型单次只能处理8192个token，并且条数不可以超过10条，所以需要分批次添加到向量数据库中
            if(documents.size() > 10){
                for (int i = 0; i < documents.size(); i += 10) {
                    int end = Math.min(i + 10, documents.size());
                    List<Document> batch = documents.subList(i, end);
                    vectorStore.add(batch);
                }
            } else {
                vectorStore.add(documents);
            }
        } catch (IOException e) {
            log.error("Error reading markdown files: ", e);
        }
    }
    
    
}
