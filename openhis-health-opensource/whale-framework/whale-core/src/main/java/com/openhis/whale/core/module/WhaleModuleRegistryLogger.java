/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.module;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 启动时输出已注册扩展模块 DAG。
 */
@Component
public class WhaleModuleRegistryLogger implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(WhaleModuleRegistryLogger.class);

    private final List<WhaleModuleDescriptor> descriptors;

    public WhaleModuleRegistryLogger(List<WhaleModuleDescriptor> descriptors) {
        this.descriptors = descriptors;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (descriptors == null || descriptors.isEmpty()) {
            return;
        }
        descriptors.stream()
                .sorted(Comparator.comparing(WhaleModuleDescriptor::moduleId))
                .forEach(d -> log.info(
                        "Whale module: id={}, enabledProperty={}, dependsOn={}, features={}",
                        d.moduleId(),
                        d.enabledProperty(),
                        String.join(",", d.dependsOnModuleIds()),
                        String.join(",", d.featureKeys())));
    }
}
