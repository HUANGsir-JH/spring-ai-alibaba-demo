package org.huang.saademo;

import jakarta.annotation.Resource;
import org.huang.saademo.service.MultiAgentService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class MultiAgentTests {
    
    @Resource
    private MultiAgentService multiAgentService;
    
    @Test
    void testSequentialAgentCall(){
        String userInput = "请写一篇关于人工智能的简要博客，字数控制在100左右，要求内容翔实，结构清晰，最后总结一下人工智能的未来发展趋势。";
        multiAgentService.sequentialAgentCall(userInput);
    }
    
    @Test
    void testParallelAgentCall(){
        String userInput = "以深圳为主题";
        multiAgentService.parallelAgentCall(userInput);
    }
    
    @Test
    void testLlmRoutingAgentCall(){
        String writeAgentInput = "请写一篇关于人工智能的简要博客，字数控制在100左右，要求内容翔实，结构清晰，最后总结一下人工智能的未来发展趋势。";
        String poemAgentInput = "请以深圳为主题，写一首七言律诗。";
        String translationAgentInput = "请将以下中文翻译成英文：深圳是中国改革开放的窗口，也是创新创业的热土。";
        multiAgentService.llmRoutingAgentCall(writeAgentInput);
        multiAgentService.llmRoutingAgentCall(poemAgentInput);
        multiAgentService.llmRoutingAgentCall(translationAgentInput);
    }
    
    @Test
    void testSupervisorAgentCall(){
        String userInput = "请写一篇关于人工智能的简要博客，字数控制在100左右，要求内容翔实，结构清晰，最后总结一下人工智能的未来发展趋势。并且翻译成英文。";
        multiAgentService.supervisorAgentCall(userInput);
    }
    
}
