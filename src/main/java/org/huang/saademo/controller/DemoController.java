package org.huang.saademo.controller;

import jakarta.annotation.Resource;
import lombok.Getter;
import org.huang.saademo.service.DemoAgent;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/demo")
public class DemoController {
    @Resource
    private DemoAgent demoAgent;
    
    @GetMapping("/weather")
    public String getWeatherInfo(@RequestParam String city) {
        if(city == null || city.trim().isEmpty()) {
            return "City name cannot be empty.";
        }
        return demoAgent.agentInvoke(city.trim());
    }
    
}
