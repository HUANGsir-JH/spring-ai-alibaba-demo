package org.huang.saademo.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class WeatherSearchTool  {
    @Tool(name="searchWeather",description = "Searches for the current weather in a specified city.")
    public String searchWeather(@ToolParam(description = "the name of city, like: shenzhen") String city){
        return "The weather in " + city + " is sunny with a high of 25°C and a low of 15°C.";
    }
}
