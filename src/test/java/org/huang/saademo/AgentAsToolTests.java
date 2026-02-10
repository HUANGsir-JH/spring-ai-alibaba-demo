package org.huang.saademo;

import jakarta.annotation.Resource;
import org.huang.saademo.service.AgentAsToolService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class AgentAsToolTests {
    
    @Resource
    private AgentAsToolService agentAsToolService;
    
    @Test
    void testAgentAsTool(){
        String input = "帮我写一篇冬天的英文短诗，并且翻译为中文";
        agentAsToolService.multiToolAgentCall(input);
    }
}
