package com.queryai.datasource;

import com.queryai.config.QueryAiProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSourceBeans {

    @Bean
    public CredentialCipher credentialCipher(QueryAiProperties properties) {
        return new CredentialCipher(properties.security().credentialKey());
    }

    @Bean
    public HostGuard hostGuard(QueryAiProperties properties) {
        return new HostGuard(properties.datasource().blockedHosts());
    }
}
