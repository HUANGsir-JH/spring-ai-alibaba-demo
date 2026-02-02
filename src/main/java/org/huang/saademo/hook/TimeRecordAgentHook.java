package org.huang.saademo.hook;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.JumpTo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
@HookPositions(value = {HookPosition.BEFORE_AGENT, HookPosition.AFTER_AGENT})
public class TimeRecordAgentHook extends AgentHook {
    @Override
    public String getName() {
        return "TimeRecordAgentHook";
    }
    
    @Override
    public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config) {
        log.info("=== Agent Start Time: {} ===", System.currentTimeMillis());
        return CompletableFuture.completedFuture(Map.of("startTime", System.currentTimeMillis()));
    }
    
    @Override
    public CompletableFuture<Map<String, Object>> afterAgent(OverAllState state, RunnableConfig config) {
        log.info("=== Agent End Time: {} ===", System.currentTimeMillis());
        Optional<Object> startTime = state.value("startTime");
        if(startTime.isPresent()){
            long duration = System.currentTimeMillis() - (Long)startTime.get();
            log.info("=== Agent Total Duration: {} ms ===", duration);
        } else {
            log.warn("Start time not found in state.");
        }
        return CompletableFuture.completedFuture(Map.of());
    }
}
