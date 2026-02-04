package org.huang.saademo.controller;

import jakarta.annotation.Resource;
import org.huang.saademo.manager.SSEManager;
import org.huang.saademo.service.StreamMemService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/stream/mem")
public class StreamMemController {
    
    @Resource
    private StreamMemService streamMemService;
    
    @Resource
    private SSEManager sseManager;
    
    @GetMapping("/agent")
    public SseEmitter streamAgent(String prompt, String sessionId) {
        SseEmitter emitter = sseManager.createEmitter(sessionId);
        streamMemService.streamCall(prompt, sessionId);
        return emitter;
    }


}
