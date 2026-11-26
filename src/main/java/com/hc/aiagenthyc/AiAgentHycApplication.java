package com.hc.aiagenthyc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the interview knowledge assistant.
 *
 * <p>Component scanning starts from this package, so controllers, the {@code KnowledgeAssistant} service,
 * model routing, attachment storage and RAG configuration under {@code com.hc.aiagenthyc} are all picked up.</p>
 */
@SpringBootApplication
public class AiAgentHycApplication {

    /** Starts the application context and the embedded web server. */
    public static void main(String[] args) {
        SpringApplication.run(AiAgentHycApplication.class, args);
    }

}
