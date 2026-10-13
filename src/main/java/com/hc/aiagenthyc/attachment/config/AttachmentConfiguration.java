package com.hc.aiagenthyc.attachment.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AttachmentProperties.class)
public class AttachmentConfiguration { }
