package org.huang.saademo.response.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AIResponseFormat {
    private String punnyResponse; // 俏皮回答
    private String weatherCondition; // 天气状况
}
