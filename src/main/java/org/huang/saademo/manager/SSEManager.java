package org.huang.saademo.manager;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class SSEManager {
    private Map<String, SseEmitter> sseHolder = new ConcurrentHashMap<>();
    
    public SseEmitter createEmitter(String sessionId) {
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L); // 10分钟超时
        
        // 关键点：使用 remove(k, v) 而不是 remove(k)
        emitter.onCompletion(() -> {
            log.info("SSE completed for session: {}", sessionId);
            sseHolder.remove(sessionId, emitter);
        });
        
        emitter.onTimeout(() -> {
            log.warn("SSE timeout for session: {}", sessionId);
            sseHolder.remove(sessionId, emitter);
            emitter.complete();
        });
        
        emitter.onError((e) -> {
            log.error("SSE error for session: {}, error: {}", sessionId, e.getMessage());
            sseHolder.remove(sessionId, emitter);
        });
        
        SseEmitter oldEmitter = sseHolder.put(sessionId, emitter);
        if (oldEmitter != null) {
            log.info("SSE Session[{}]: Old connection replaced", sessionId);
            try {
                oldEmitter.complete(); // 显式关闭旧连接，释放资源
            } catch (Exception ignored) {}
        }
        
        log.info("SSE Session[{}]: New connection registered", sessionId);
        return emitter;
    }
    
    public SseEmitter getEmitter(String sessionId){
        return sseHolder.get(sessionId);
    }
    
    public void completeEmitter(String sessionId){
        SseEmitter emitter = sseHolder.remove(sessionId);
        if(emitter != null){
            try{
                emitter.complete();
            }catch (Exception e){
                log.error("SSEManager completeEmitter with sessionId: {} found error: {}",
                        sessionId, e.getMessage());
            }
        }
    }
    
    public void completeEmitterWithError(String sessionId, Throwable t){
        SseEmitter emitter = sseHolder.remove(sessionId);
        if(emitter != null){
            synchronized (emitter) {
                try{
                    emitter.completeWithError(t);
                } catch (Exception e){
                    log.error("SSEManager completeEmitterWithError with sessionId: {} found error: {}",
                            sessionId, e.getMessage());
                }
            }
        }
    }
    
//    public void sendEvent(String sessionId, String name, Object data){
//        SseEmitter emitter = sseHolder.get(sessionId);
//        if(emitter == null){
//            log.warn("SSEManager sendEvent: No emitter found for sessionId: {}", sessionId);
//            return;
//        }
//
//        synchronized (emitter) {
//            try{
//                emitter.send(SseEmitter.event().name(name).data(data));
//            } catch (IOException | IllegalStateException e) {
//                // IOException: 客户端断开连接
//                // IllegalStateException: 订阅已关闭/完成
//                log.warn("SSE send failed for session: {}, reason: {}", sessionId, e.getMessage());
//                cleanup(sessionId, emitter);
//            } catch (Exception e) {
//                log.error("Unexpected error sending SSE for session: {}", sessionId, e);
//                cleanup(sessionId, emitter);
//            }
//        }
//    }
    
    private void cleanup(String sessionId, SseEmitter emitter) {
        sseHolder.remove(sessionId, emitter);
        try {
            emitter.complete();
        } catch (Exception e) {
            // 彻底忽略尝试关闭已损坏连接的错误
        }
    }
    
    /**
     * 直接对指定的 Emitter 实例发送数据，而不是从 Map 里找
     */
    public void sendEvent(SseEmitter emitter, String sessionId, String name, Object data) {
        if (emitter == null) return;
        
        synchronized (emitter) {
            try {
                emitter.send(SseEmitter.event().name(name).data(data));
            } catch (Exception e) {
                log.warn("Direct send failed for session: {}, reason: {}", sessionId, e.getMessage());
                cleanup(sessionId, emitter); // 使用之前写的双参数 remove 清理
            }
        }
    }
}
