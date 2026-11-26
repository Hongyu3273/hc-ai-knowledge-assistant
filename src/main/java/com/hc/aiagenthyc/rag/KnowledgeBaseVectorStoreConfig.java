package com.hc.aiagenthyc.rag;


import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.nio.file.Path;

/**
 * Configures the local vector store for the bundled knowledge base. The store is created on the first RAG request;
 * when persistence is on, a saved index is reused while the documents and embedding model are unchanged.
 */
@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class KnowledgeBaseVectorStoreConfig {

    /**
     * Loads the question banks and returns a vector store over them. Lazy, so startup does not call the embedding
     * model; the first use either loads a matching saved index or embeds every passage and, when persistence is on,
     * writes the index files.
     */
    @Bean("knowledgeBaseVectorStore")
    @Lazy
    VectorStore knowledgeBaseVectorStore(
            @Qualifier("dashscopeEmbeddingModel") EmbeddingModel embeddingModel,
            KnowledgeBaseLoader documentLoader,
            RagProperties ragProperties,
            // Only used to detect a model change; the value need not match the provider's own default name.
            @Value("${spring.ai.dashscope.embedding.options.model:provider-default}") String embeddingModelName) {
        RagProperties.Index index = ragProperties.getIndex();
        PersistentVectorIndex vectorIndex = new PersistentVectorIndex(embeddingModel, "dashscope:" + embeddingModelName,
                index.isPersist() ? Path.of(index.getDirectory()) : null);
        return vectorIndex.loadOrBuild(documentLoader.loadMarkdown());
    }

}
