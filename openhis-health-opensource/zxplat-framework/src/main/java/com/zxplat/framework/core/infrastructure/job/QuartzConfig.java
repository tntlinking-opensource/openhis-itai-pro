/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.infrastructure.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

/**
 * Quartz配置类
 *
 * <p>使用 Spring Boot 的 Quartz 自动配置，只自定义 JobFactory 以支持 Spring Bean 注入</p>
 * <p>Spring Boot 会自动创建 SchedulerFactoryBean 和 Scheduler，我们只需要提供 JobFactory</p>
 *
 * @author OpenHIS Contributors
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.quartz.enabled", havingValue = "true", matchIfMissing = true)
public class QuartzConfig {

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 配置JobFactory，支持Spring Bean注入
     *
     * <p>Spring Boot 的 QuartzAutoConfiguration 会自动检测名为 jobFactory 的 Bean，
     * 如果存在就使用它来配置 SchedulerFactoryBean</p>
     */
    @Bean
    public SpringBeanJobFactory jobFactory() {
        AutowiringSpringBeanJobFactory jobFactory = new AutowiringSpringBeanJobFactory();
        jobFactory.setApplicationContext(applicationContext);
        return jobFactory;
    }

    /**
     * 自定义JobFactory，支持Spring Bean自动注入
     */
    private static class AutowiringSpringBeanJobFactory extends SpringBeanJobFactory {
        private transient org.springframework.beans.factory.config.AutowireCapableBeanFactory beanFactory;

        public void setApplicationContext(ApplicationContext applicationContext) {
            this.beanFactory = applicationContext.getAutowireCapableBeanFactory();
        }

        @Override
        protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
            Object job = super.createJobInstance(bundle);
            if (beanFactory != null) {
                beanFactory.autowireBean(job);
            }
            return job;
        }
    }
}
