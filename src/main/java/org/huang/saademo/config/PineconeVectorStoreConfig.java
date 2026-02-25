package org.huang.saademo.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;
import jakarta.annotation.Resource;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.vectorstore.pinecone.PineconeVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;


@Configuration
public class PineconeVectorStoreConfig {
    
    @Value("${spring.ai.vectorstore.pinecone.index-name}")
    private String pineconeIndexName;
    @Value("${spring.ai.vectorstore.pinecone.api-key}")
    private String pineconeApiKey;
    
    @Resource
    private ApiKeyConfig apiKeyConfig;
    private static final int VECTOR_DIMENSION = 1024;
    private static final String MODEL_NAME = "text-embedding-v4";
    
    @Bean(name="dashscopeEmbeddingModel-v4")
    @Primary
    public EmbeddingModel dashscopeEmbeddingModel(){
        DashScopeApi api = DashScopeApi.builder().apiKey(apiKeyConfig.getQwenKey()).build();
        DashScopeEmbeddingOptions options = DashScopeEmbeddingOptions.builder()
                .model(MODEL_NAME)
                .textType("document")
                .dimensions(VECTOR_DIMENSION)
                .build();
        return new DashScopeEmbeddingModel(
                api,
                MetadataMode.EMBED,
                options,
                RetryUtils.DEFAULT_RETRY_TEMPLATE
        );
    }
    
    @Bean(name="customPineconeVectorStore")
    public PineconeVectorStore pineconeVectorStore() {
        return PineconeVectorStore.builder(dashscopeEmbeddingModel())
                .apiKey(pineconeApiKey)
                .indexName(pineconeIndexName)
                .contentFieldName("content")
                .batchingStrategy(new TokenCountBatchingStrategy())
                .build();
    }

}
