package com.hc.aiagenthyc.rag;


import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import static org.assertj.core.api.Assertions.assertThat;

class LoveAppDocumentLoaderTest {

    @Test
    void loadsBundledRelationshipDocuments() {
        var loader = new LoveAppDocumentLoader(new PathMatchingResourcePatternResolver());
        assertThat(loader.loadMarkdown()).isNotEmpty();
    }
}
