package com.inspire17.ythelper.config.storage;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class GcpConfig {

    @Value("${storage.env}")
    private String storageEnv;

    @Bean
    @ConditionalOnProperty(name = "storage.env", havingValue = "GCP_BUCKET")
    public Storage storage() {
        return StorageOptions.getDefaultInstance().getService();
    }
}