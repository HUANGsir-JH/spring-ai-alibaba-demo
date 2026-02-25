package org.huang.saademo.hook;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.search.Expression;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.pinecone.PineconeVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
@HookPositions(value = {HookPosition.BEFORE_MODEL}) // 指定该 Hook 在模型调用前执行
public class RAGHook extends MessagesModelHook {
    
    private PineconeVectorStore vectorStore;
    
    @Autowired
    public RAGHook(PineconeVectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }
    
    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
        // 获取用户最新输入的消息
        Message latestUserMessage = getLatestUserMessage(previousMessages);
        if(latestUserMessage!=null){
            String query = latestUserMessage.getText();
            if(query!=null && !query.isEmpty()){
                // 在向量数据库中进行检索，获取相关的上下文信息
                List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                        .query(query)
                        .similarityThreshold(0.8) // 设置相似度阈值，根据实际情况调整
                        .topK(5) // 设置返回的结果数量，根据实际情况调整
                        .build()
                );
                
                String rags = documents.stream().map(Document::getText).collect(Collectors.joining("\n"));
                List<Message> enhancedMessages = new ArrayList<>(previousMessages);
                enhancedMessages.add(new SystemMessage("根据用户最新输入，在知识库中检索到以下相关信息：\n" + rags));
                return new AgentCommand(enhancedMessages, UpdatePolicy.REPLACE);
            }
        }
        return super.beforeModel(previousMessages, config);
    }
    
    @Override
    public String getName() {
        return "RAGHook";
    }
    
    private Message getLatestUserMessage(List<Message> messages) {
        // 从消息列表中获取最新的用户消息
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if(message instanceof UserMessage){
                return message;
            }
        }
        return null; // 如果没有找到用户消息，返回 null
    }
}
