package com.hc.aiagenthyc.rag;


import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.List;

/**
 * Configures the in-memory vector store used for document embeddings.
 */
@Configuration
public class LoveAppVectorStoreConfig {

    @Bean("loveAppVectorStore")
    @Lazy
    VectorStore loveAppVectorStore(
            @Qualifier("dashscopeEmbeddingModel") EmbeddingModel embeddingModel,
            LoveAppDocumentLoader documentLoader) {

        SimpleVectorStore simpleVectorStore =
                SimpleVectorStore.builder(embeddingModel).build();

        List<Document> documentList = documentLoader.loadMarkdown();
        if (!documentList.isEmpty()) {
            simpleVectorStore.add(documentList);
        }

        return simpleVectorStore;
    }

}
