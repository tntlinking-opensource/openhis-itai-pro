/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * 关闭 Boot 默认 Flyway 时，补齐「迁移先于 JPA / Quartz」的依赖顺序（等价于 {@code EntityManagerFactoryDependsOnPostProcessor}）。
 */
@Component
public class FlywayBeforeQuartzConfig implements BeanDefinitionRegistryPostProcessor, Ordered {

    private static final String FLYWAY_INITIALIZER_BEAN = "flywayInitializer";
    private static final String ENTITY_MANAGER_FACTORY_BEAN = "entityManagerFactory";
    private static final String QUARTZ_SCHEDULER_BEAN = "quartzScheduler";

    /**
     * 在 Bean 定义注册阶段为 JPA 与 Quartz 注入对 flywayInitializer 的 depends-on。
     *
     * @param registry Spring Bean 定义注册表
     */
    @Override
    public void postProcessBeanDefinitionRegistry(@NonNull BeanDefinitionRegistry registry) throws BeansException {
        // 未启用模块化 Flyway 时无需调整依赖顺序
        if (!registry.containsBeanDefinition(FLYWAY_INITIALIZER_BEAN)) {
            return;
        }
        // JPA 须在 schema 迁移完成后初始化，避免实体映射到未建表
        if (registry.containsBeanDefinition(ENTITY_MANAGER_FACTORY_BEAN)) {
            prependDependsOn(registry, ENTITY_MANAGER_FACTORY_BEAN, FLYWAY_INITIALIZER_BEAN);
        }
        // Quartz 任务可能依赖已迁移表，须晚于 Flyway
        if (registry.containsBeanDefinition(QUARTZ_SCHEDULER_BEAN)) {
            prependDependsOn(registry, QUARTZ_SCHEDULER_BEAN, FLYWAY_INITIALIZER_BEAN);
        }
    }

    /**
     * 将 dependsOnBean 前置到 targetBean 的 depends-on 链，避免重复声明。
     */
    private static void prependDependsOn(
            BeanDefinitionRegistry registry, String targetBean, String dependsOnBean) {
        BeanDefinition bd = registry.getBeanDefinition(targetBean);
        String[] dependsOn = bd.getDependsOn();
        if (dependsOn == null) {
            bd.setDependsOn(dependsOnBean);
            return;
        }
        for (String d : dependsOn) {
            if (dependsOnBean.equals(d)) {
                return;
            }
        }
        String[] newDependsOn = new String[dependsOn.length + 1];
        newDependsOn[0] = dependsOnBean;
        System.arraycopy(dependsOn, 0, newDependsOn, 1, dependsOn.length);
        bd.setDependsOn(newDependsOn);
    }

    @Override
    public void postProcessBeanFactory(@NonNull ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // no-op
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
