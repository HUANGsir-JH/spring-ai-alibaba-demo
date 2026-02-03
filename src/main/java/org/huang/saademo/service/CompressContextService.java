package org.huang.saademo.service;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import jakarta.annotation.Resource;
import org.huang.saademo.config.ApiKeyConfig;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CompressContextService {
    
    @Resource
    private ApiKeyConfig apiKeyConfig;
    
    private static final String MODEL_NAME = "qwen-flash";
    
    public String compressContext(List<Message> messages) {
        ChatModel chatModel = genChatModel();
        List<Message> messagesWithPrompt = new ArrayList<>();
        messagesWithPrompt.add(new SystemMessage(genSystemPrompt()));
        messagesWithPrompt.addAll(messages);
        
        ChatResponse response = chatModel.call(new Prompt(messagesWithPrompt));
        return response.getResult().getOutput().getText();
    }
    
    private ChatModel genChatModel() {
        DashScopeApi api = DashScopeApi.builder().apiKey(apiKeyConfig.getQwenKey()).build();
        
        DashScopeChatOptions options = DashScopeChatOptions.builder().model(MODEL_NAME).build();
        
        DashScopeChatModel chatModel = DashScopeChatModel.builder().dashScopeApi(api).defaultOptions(options).build();
        
        return chatModel;
    }
    
    private String genSystemPrompt(){
        return """
                请作为一名高效的信息架构师，对以下内容进行“高保真压缩”。
                目标： 在保留所有核心逻辑、关键事实和重要数据的前提下，最大限度地减少字数。
                压缩规则：
                1. 去冗余： 剔除所有修饰性形容词、礼貌用语、重复论述和背景废话。
                2. 高密度转换： 使用符号（->, :, &）、短语和专业术语代替长难句。
                3. 保留核心： 必须完整保留人名、日期、具体数值、结论以及尚未解决的争议点。
                4. 逻辑重组： 如果原内容零散，请按“背景 > 现状 > 结论/行动”的结构重新梳理。
                5. 对于较新的对话内容，尽量做到全量保留，比如最近三轮对话。
                6. 只输出压缩后的内容，禁止输出任何解释、注释或额外信息。
                7. 输出语言必须与输入内容一致。
                """;
    }
    
    
}
