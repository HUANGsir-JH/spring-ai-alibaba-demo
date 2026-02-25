package org.huang.saademo.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pinecone.PineconeVectorStore;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RAGTool {
    
    @Resource(name = "customPineconeVectorStore")
    private PineconeVectorStore vectorStore;
    
    private record SearchResult(boolean isSuccess, String content, String errorMsg) {}
    
    private ObjectMapper objectMapper = new ObjectMapper();
    
    
    @Tool(
            name = "RAGTool",
            description = "一个基于Pinecone向量数据库的检索工具，输入一个查询语句和相关参数，返回相关的文本内容"
    )
    public String retrieve(
            @ToolParam(description = "需要查询的内容") String query,
            @ToolParam(description = "需要返回的相关内容的条数，默认为5") Integer topK,
            @ToolParam(description = "信息的相关性阈值，默认为0.8") Double scoreThreshold
    ){
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK != null ? topK : 5)
                .similarityThreshold(scoreThreshold != null ? scoreThreshold : 0.8)
                .build();
        
        List<Document> documents = vectorStore.similaritySearch(request);
        if(!documents.isEmpty()){
            StringBuilder sb = new StringBuilder();
            for (Document doc : documents) {
                sb.append(doc.getText()).append("\n\n");
            }
            
            SearchResult searchResult = new SearchResult(true, sb.toString(), null);
            try {
                return objectMapper.writeValueAsString(searchResult);
            } catch (Exception e) {
                SearchResult errorResult = new SearchResult(false, null, "序列化结果时发生错误: " + e.getMessage());
                try {
                    return objectMapper.writeValueAsString(errorResult);
                } catch (Exception ex) {
                    return "发生错误: " + ex.getMessage();
                }
            }
        } else {
            SearchResult searchResult = new SearchResult(false, null, "未找到相关内容");
            try {
                return objectMapper.writeValueAsString(searchResult);
            } catch (Exception e) {
                return "发生错误: " + e.getMessage();
            }
        }
        
    }
}
