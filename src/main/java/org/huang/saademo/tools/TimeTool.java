package org.huang.saademo.tools;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;

import static com.alibaba.cloud.ai.graph.agent.tools.ToolContextConstants.AGENT_CONFIG_CONTEXT_KEY;

@Component
@Slf4j
public class TimeTool {
    
    @Tool(name="getCurrentTime",description = "Get the current time in YYYY-MM-DD HH:MM:SS format")
    public String getCurrentTime(ToolContext toolContext) {
        RunnableConfig config = (RunnableConfig) toolContext.getContext().get(AGENT_CONFIG_CONTEXT_KEY);
        Optional<Object> id = config.metadata("user_id");
        id.ifPresent(userId -> log.info("User [{}] invoked TimeTool at {}", userId, new Date()));
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }
}
