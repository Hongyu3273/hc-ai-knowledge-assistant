package com.hc.aiagenthyc.attachment.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers {@link AttachmentProperties} so the {@code app.attachments.*} settings are bound and validated. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AttachmentProperties.class)
public class AttachmentConfiguration { }
