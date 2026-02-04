package org.huang.saademo.hook;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.huang.saademo.common.Constants;
import org.huang.saademo.manager.SSEManager;
import org.huang.saademo.service.CompressContextService;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
@HookPositions(value = {HookPosition.BEFORE_MODEL}) // 指定该 Hook 在模型调用前执行
public class MessageManageHook extends MessagesModelHook {
    @Override
    public String getName() {
        return "MessageManageHook";
    }
    
    @Resource
    private SSEManager sseManager;
    
    @Resource
    private CompressContextService compressContextService;
    
    // 100K tokens 上限，触发就会进行上下文压缩
    private static final int TOKEN_LIMIT = 100000; // 100K tokens
    
    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
        // 在这里可以对 previousMessages 进行管理，例如添加、修改或删除消息
        List<Message> messages = delEmptyMessages(previousMessages);
        int totalTokens = calculateTokenCount(messages);
        if (totalTokens > TOKEN_LIMIT) {
            Optional<String> s = config.threadId();
            if(s.isPresent()){
                log.info("Session ID: {}, Total Tokens: {} exceed limit: {}, performing context compression.", s.get(), totalTokens, TOKEN_LIMIT);
            }else{
                log.info("Total Tokens: {} exceed limit: {}, performing context compression.", totalTokens, TOKEN_LIMIT);
            }
            long startTime = System.currentTimeMillis();
            // 超过 Token 限制，进行上下文压缩
            SseEmitter emitter = sseManager.getEmitter(s.orElse("N/A"));
            if (emitter != null) {
                sseManager.sendEvent(emitter,s.orElse("N/A"), Constants.SSE_EVENT_CONTEXT, "Context tokens: " + totalTokens + " exceed limit: " + TOKEN_LIMIT + ", performing compression...");
            }
            String compressedContext = compressContextService.compressContext(messages);
            List<Message> newMessages = List.of(new UserMessage(compressedContext));
            long endTime = System.currentTimeMillis();
            log.info("Session ID: {}, Context compression completed in {} ms.", s.orElse("N/A"), (endTime - startTime));
            return new AgentCommand(newMessages, UpdatePolicy.REPLACE);
        }
        
        return new AgentCommand(previousMessages);
    }
    
    /**
     * 计算消息列表的总 Token 数量，此处仅仅是简单估算
     * 由于不同模型的分词器不同，实际 Token 数量可能会有所差异
     * @param messages
     * @return 估算的 Token 数量
     */
    private int calculateTokenCount(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        
        AtomicInteger totalTokens = new AtomicInteger();
        
        for (Message message : messages) {
            // 1. 基础开销：每条消息包含角色和格式占用的 Token (通常估算为 4)
            totalTokens.addAndGet(4);
            
            // 2. 计算正文 Token
            String text = message.getText();
            if (text != null && !text.isEmpty()) {
                totalTokens.addAndGet(estimateStringTokens(text));
            }
            
            // 3. 计算消息类型 (MessageType)
            if (message.getMessageType() != null) {
                totalTokens.addAndGet(estimateStringTokens(message.getMessageType().toString()));
            }
            
            // 4. 计算元数据 (Metadata) - 如果存在，通常是 Key-Value 形式
            if (message.getMetadata() != null && !message.getMetadata().isEmpty()) {
                message.getMetadata().forEach((key, value) -> {
                    totalTokens.addAndGet(estimateStringTokens(key));
                    if (value != null) {
                        totalTokens.addAndGet(estimateStringTokens(value.toString()));
                    }
                });
            }
        }
        
        // 5. 整个对话结束的固定开销
        totalTokens.addAndGet(3);
        
        return totalTokens.get();
    }
    
    private List<Message> delEmptyMessages(List<Message> messages) {
        return messages.stream()
                .filter(message -> message.getText() != null && !message.getText().trim().isEmpty())
                .toList();
    }
    
    private int estimateStringTokens(String content) {
        if (content == null || content.isEmpty()) return 0;
        
        double tokenCount = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            // 简单判断：如果是 ASCII 字符（英文字母、数字、普通标点）
            if (c <= 127) {
                // 英文和数字平均 0.25 ~ 0.5 个 token (4个字母约等于1个token)
                // 但为了保险，我们按 0.5 算
                tokenCount += 0.5;
            } else {
                // 中文字符、复杂符号，通常一个字符就是 1.5 到 2 个 token
                tokenCount += 1.5;
            }
        }
        // 向上取整，并加上基础权重
        return (int) Math.ceil(tokenCount);
    }
}
