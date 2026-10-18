/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.runtime.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 框架运行时同步专用线程池：队列满时丢弃任务，不阻塞调用方。
 */
@Configuration
public class FrameworkRuntimeAsyncConfiguration {

    public static final String EXECUTOR_BEAN_NAME = "frameworkRuntimeExecutor";

    @Bean(name = EXECUTOR_BEAN_NAME)
    public Executor frameworkRuntimeExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(512);
        executor.setThreadNamePrefix("fw-runtime-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }
}
