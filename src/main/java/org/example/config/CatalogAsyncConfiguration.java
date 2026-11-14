package org.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Отдельный ограниченный пул для блокирующих запросов Hibernate/JDBC. */
@Configuration
@Profile("db")
@EnableAsync
public class CatalogAsyncConfiguration {
    @Bean
    public ThreadPoolTaskExecutor catalogExecutor(@Value("${catalog.async.pool-size:4}") int poolSize) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("catalog-db-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
