package org.huang.saademo.controller;

import jakarta.annotation.Resource;
import org.huang.saademo.service.RAGAgentService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rag")
public class RAGAgentController {
    
    @Resource
    private RAGAgentService ragAgentService;
    
    @RequestMapping("/call")
    public String callAgent(String userInput){
        return ragAgentService.agentCall(userInput).toString();
    }
}
