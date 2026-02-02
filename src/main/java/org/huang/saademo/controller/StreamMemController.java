package org.huang.saademo.controller;

import jakarta.annotation.Resource;
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
    
    private SseEmitter generateEmitter() {
        // 设置5分钟超时
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);
        emitter.onCompletion(() -> {
            System.out.println("SSE stream completed.");
        });
        emitter.onTimeout(() -> {
            System.out.println("SSE stream timed out.");
            emitter.complete();
        });
        return emitter;
    }
    
    @GetMapping("/agent")
    public SseEmitter streamAgent(String prompt) {
        SseEmitter emitter = generateEmitter();
        streamMemService.streamCall(emitter, prompt);
        return emitter;
    }


}
