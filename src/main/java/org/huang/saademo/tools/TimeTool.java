package org.huang.saademo.tools;

import org.springframework.ai.tool.annotation.Tool;

import java.text.SimpleDateFormat;
import java.util.Date;

public class TimeTool {
    
    @Tool(description = "Get the current time in YYYY-MM-DD HH:MM:SS format")
    public String getCurrentTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }
}
