package org.huang.saademo.controller;

import jakarta.annotation.Resource;
import org.huang.saademo.manager.SSEManager;
import org.huang.saademo.service.AgentAsToolService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/agent-as-tool")
public class AgentAsToolController {
    @Resource
    private SSEManager sseManager;
    
    @Resource
    private AgentAsToolService agentAsToolService;
    
    @GetMapping("/tool")
    public SseEmitter multiToolAgentCall(String input){
        SseEmitter emitter = sseManager.createEmitter();
        agentAsToolService.multiToolAgentStreamCall(emitter, input);
        return emitter;
    }
    
    
}
