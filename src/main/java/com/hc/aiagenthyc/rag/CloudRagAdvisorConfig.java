package com.hc.aiagenthyc.rag;


import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetriever;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetrieverOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * Cloud retrieval from a DashScope knowledge-base index. Created only when {@code app.assistant.rag.cloud.enabled=true};
 * the index must already exist in the DashScope console.
 *
 * <p>Retrieval runs on DashScope's side, so the local {@code topK}, {@code similarityThreshold} and index settings in
 * {@link RagProperties} do not apply to this path.</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "app.assistant.rag.cloud", name = "enabled", havingValue = "true")
public class CloudRagAdvisorConfig {

    /**
     * Builds a retrieval-augmentation advisor whose retriever queries the configured DashScope index. Each advised call
     * makes a remote retrieval request; the retrieved documents are added to the prompt and exposed in the response
     * context under {@link RetrievalAugmentationAdvisor#DOCUMENT_CONTEXT}.
     *
     * @param dashScopeApiKey DashScope API key, shared with the chat and embedding models
     * @param ragProperties   supplies the cloud index name
     */
    @Bean
    public Advisor cloudRagAdvisor(
            @Value("${spring.ai.dashscope.api-key}") String dashScopeApiKey,
            RagProperties ragProperties) {
        DashScopeApi dashScopeApi = DashScopeApi.builder().apiKey(dashScopeApiKey).build();
        DashScopeDocumentRetriever dashScopeDocumentRetriever = new DashScopeDocumentRetriever(dashScopeApi,
                DashScopeDocumentRetrieverOptions.builder()
                        .indexName(ragProperties.getCloud().getIndexName())
                        .build());
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(dashScopeDocumentRetriever)
                .build();
    }
}
