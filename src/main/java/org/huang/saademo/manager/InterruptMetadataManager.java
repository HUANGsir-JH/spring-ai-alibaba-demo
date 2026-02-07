package org.huang.saademo.manager;

import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class InterruptMetadataManager {
    // 使用 final 确保引用不可变
    private final ConcurrentHashMap<String, InterruptionMetadata> metadataMap = new ConcurrentHashMap<>();
    
    // 简单的 put/get/remove 操作
    public void put(String threadId, InterruptionMetadata metadata) {
        if (metadata == null) {
            throw new IllegalArgumentException("metadata cannot be null");
        }
        metadataMap.put(threadId, metadata);
    }
    
    public InterruptionMetadata get(String threadId) {
        return metadataMap.get(threadId);
    }
    
    public InterruptionMetadata remove(String threadId) {
        return metadataMap.remove(threadId);
    }
    
    // 原子性复合操作，先检查再添加，如果已存在则不覆盖，否则添加
    public InterruptionMetadata putIfAbsent(String threadId, InterruptionMetadata metadata) {
        return metadataMap.putIfAbsent(threadId, metadata);
    }
    
    public boolean containsKey(String threadId) {
        return metadataMap.containsKey(threadId);
    }
    
    public void clear() {
        metadataMap.clear();
    }
    
}
