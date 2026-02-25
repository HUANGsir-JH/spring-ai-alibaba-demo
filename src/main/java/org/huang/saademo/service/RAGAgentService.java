package org.huang.saademo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.huang.saademo.config.ApiKeyConfig;
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

@Service
@Slf4j
public class RAGAgentService {
    @Resource
    private ApiKeyConfig apiKeyConfig;
    
    @Resource(name= "streamAgentTaskExecutor")
    private ThreadPoolTaskExecutor executor;
    
    @Resource(name = "customPineconeVectorStore")
    private PineconeVectorStore vectorStore;
    
    private static final String MODEL_NAME = "qwen3.5-plus";
    
    public record AgentOutput(String outputType, String agentName, String type, String text, Map<String, Object> metadata) {}
    
    private ObjectMapper objectMapper = new ObjectMapper();
    
    
    
    
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
