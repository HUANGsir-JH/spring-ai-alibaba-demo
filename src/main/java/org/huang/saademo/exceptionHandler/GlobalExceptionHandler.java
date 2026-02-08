package org.huang.saademo.exceptionHandler;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleRuntimeException(RuntimeException ex) {
        log.error("Runtime exception occurred: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error: " + ex.getMessage());
    }
    
    @ExceptionHandler(JsonProcessingException.class)
    public ResponseEntity<String> handleJsonProcessingException(JsonProcessingException ex) {
        log.error("JSON processing error: ", ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("JSON格式错误: " + ex.getMessage());
    }
    
    @ExceptionHandler(JsonParseException.class)
    public ResponseEntity<String> handleJsonParseException(JsonParseException ex) {
        log.error("JSON parse error: ", ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("JSON解析失败: " + ex.getMessage() + 
                      ". 请检查返回的数据格式是否正确。");
    }
    
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleIllegalStateException(IllegalStateException ex) {
        log.error("Illegal state error: ", ex);
        // 特别处理JSON转换相关的非法状态异常
        if (ex.getMessage() != null && ex.getMessage().contains("Conversion from JSON")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("数据格式转换错误: " + ex.getMessage() + 
                          ". 可能是返回的数据格式不符合预期。");
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("状态错误: " + ex.getMessage());
    }
}