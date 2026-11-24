package com.dows.bitacora2.restaurante.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Pool propio para tareas en segundo plano (auditoría). Antes se usaba el ForkJoinPool
 * común de la JVM, compartido con el resto de la aplicación y sin límites ni nombre.
 */
@Slf4j
@EnableAsync
@Configuration
public class AsyncConfig {

    public static final String AUDITORIA_EXECUTOR = "auditoriaExecutor";

    @Bean(name = AUDITORIA_EXECUTOR)
    public Executor auditoriaExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("auditoria-");
        // Si la cola se llena, el evento se escribe en el hilo que lo publicó: más lento, pero no se pierde
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
