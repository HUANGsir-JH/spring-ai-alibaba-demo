package org.huang.saademo.tools;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Optional;
import java.util.function.BiFunction;

import static com.alibaba.cloud.ai.graph.agent.tools.ToolContextConstants.AGENT_CONFIG_CONTEXT_KEY;

public class UserLocationTool implements BiFunction<String, ToolContext, String> {
    @Override
    @Tool(description = "Get user location based on user ID from the context")
    public String apply(@ToolParam(description = "User query") String query,
                        ToolContext toolContext)
    {
        String userId = "";
        if(toolContext !=null && toolContext.getContext() != null){
            RunnableConfig config = (RunnableConfig) toolContext.getContext().get(AGENT_CONFIG_CONTEXT_KEY);
            Optional<Object> id = config.metadata("user_id");
            if(id.isPresent()){ // isPresent 表示存在值
                userId = (String) id.get();
            }
        }
        return "1".equals(userId) ? "User is located in New York City." : "User location is Florida.";
    }
}
