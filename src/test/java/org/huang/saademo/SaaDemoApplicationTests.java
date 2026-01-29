package org.huang.saademo;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import jakarta.annotation.Resource;
import org.huang.saademo.service.DemoAgent;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@SpringBootTest
class SaaDemoApplicationTests {
    
    @Resource
    private DemoAgent demoAgent;
    
    @Test
    void contextLoads() {
    }
    
    @Test
    void testRealAgentCall() throws GraphRunnerException {
        String id = UUID.randomUUID().toString();
        
        RunnableConfig config = RunnableConfig.builder()
                .threadId(id)
                .build();
        
        ReactAgent agent = demoAgent.genAgent();
        
        AssistantMessage message1 = agent.call("what is the weather in San Francisco today.",
                config);
        
        System.out.println("Agent Response: " + message1.getText());
        
        AssistantMessage message2 = agent.call("How about the weather tomorrow", config);
        
        System.out.println("Agent Response: " + message2.getText());
        
        AssistantMessage message3 = agent.call("What did I ask you before?", config);
        
        System.out.println("Agent Response: " + message3.getText());
    }
    
    @Test
    void testWriterAgentCall() throws GraphRunnerException {
        ReactAgent agent = demoAgent.genWriterAgent();
        
        Optional<OverAllState> allState = agent.invoke("Write a short statement about the benefits of using AI in modern applications.");
        
        if (allState.isPresent()) {
            OverAllState state = allState.get();
            List<Message> messages = state.value("messages", new ArrayList<>());
            System.out.println("Messages from Writer Agent:");
            for (Message msg : messages) {
                System.out.println("[Message]\n" + msg.getText());
            }
            System.out.println("[Full State]:\n " + state);
            
        } else {
            System.out.println("No response from Writer Agent.");
        }
    }
    
    @Test
    void testHumanInAgent() throws GraphRunnerException {
        String response = demoAgent.agentInvoke("what is the weather in San Jose today.");
        System.out.println("Final Agent Response: " + response);
    }

}
