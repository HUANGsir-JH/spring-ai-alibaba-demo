package org.huang.saademo.controller;

import jakarta.annotation.Resource;
import org.huang.saademo.manager.SSEManager;
import org.huang.saademo.service.MultiAgentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/multi-agent")
public class MultiAgentController {
    
    @Resource
    private SSEManager sseManager;
    
    @Resource
    private MultiAgentService multiAgentService;

    @GetMapping("/sequential") // 顺序执行多个Agent
    public SseEmitter sequentialAgents(@RequestParam String input) {
        SseEmitter emitter = sseManager.createEmitter();
        multiAgentService.sequentialAgentStreamCall(input, emitter);
        return emitter;
    }
    
    @GetMapping("/parallel") // 并行执行多个Agent
    public SseEmitter parallelAgents(@RequestParam String input) {
        SseEmitter emitter = sseManager.createEmitter();
        multiAgentService.parallelAgentStreamCall(input, emitter);
        return emitter;
    }
    
    @GetMapping("/routing") // 根据输入内容路由到不同Agent
    public SseEmitter routingAgents(@RequestParam String input) {
        SseEmitter emitter = sseManager.createEmitter();
        multiAgentService.llmRoutingAgentStreamCall(input, emitter);
        return emitter;
    }
    
    @GetMapping("/supervisor") // 使用SupervisorAgent协调多个Agent执行
    public SseEmitter supervisorAgents(@RequestParam String input) {
        SseEmitter emitter = sseManager.createEmitter();
        multiAgentService.supervisorAgentStreamCall(input, emitter);
        return emitter;
    }

}
