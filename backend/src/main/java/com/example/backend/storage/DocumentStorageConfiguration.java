package com.example.backend.storage;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DocumentStorageConfiguration {

    @Bean
    DocumentStorage documentStorage(DocumentStorageProperties properties) {
        return new LocalDocumentStorage(properties.root());
    }
}
