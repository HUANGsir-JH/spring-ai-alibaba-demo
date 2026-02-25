package org.huang.saademo;

import jakarta.annotation.Resource;
import org.huang.saademo.service.RAGAgentService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class RAGTests {
    
    @Resource
    private RAGAgentService ragAgentService;
    
    
    @Test
    void testAddMarkdownToVectorDB(){
        ragAgentService.addMarkdownToVectorDB();
    }
}
