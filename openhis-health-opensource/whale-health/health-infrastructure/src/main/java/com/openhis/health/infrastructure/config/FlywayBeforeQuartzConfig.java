/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.infrastructure.config;

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

    @Override
    public void postProcessBeanDefinitionRegistry(@NonNull BeanDefinitionRegistry registry) throws BeansException {
        if (!registry.containsBeanDefinition(FLYWAY_INITIALIZER_BEAN)) {
            return;
        }
        if (registry.containsBeanDefinition(ENTITY_MANAGER_FACTORY_BEAN)) {
            prependDependsOn(registry, ENTITY_MANAGER_FACTORY_BEAN, FLYWAY_INITIALIZER_BEAN);
        }
        if (registry.containsBeanDefinition(QUARTZ_SCHEDULER_BEAN)) {
            prependDependsOn(registry, QUARTZ_SCHEDULER_BEAN, FLYWAY_INITIALIZER_BEAN);
        }
    }

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
