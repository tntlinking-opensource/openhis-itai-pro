/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * CHS domain 层 JPA 与 {@code *DomainService} 扫描配置。
 *
 * <p>注册 {@code com.zxplat.plugin.chs.domain} 下实体、Repository 与领域服务 Bean，
 * 供 bridge Invoker 与 Orchestrator 注入使用。</p>
 */
@Configuration
@ConditionalOnProperty(name = "zxplat.modules.chs.enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackages = "com.zxplat.plugin.chs.domain")
@EntityScan(basePackages = {
    "com.zxplat.plugin.chs.domain.audit",
    "com.zxplat.plugin.chs.domain.catalog",
    "com.zxplat.plugin.chs.domain.notice",
    "com.zxplat.plugin.chs.domain.inpatient",
    "com.zxplat.plugin.chs.domain.inventory",
    "com.zxplat.plugin.chs.domain.outpatient",
    "com.zxplat.plugin.chs.domain.platformdict",
    "com.zxplat.plugin.chs.domain.reconciliation",
    "com.zxplat.plugin.chs.domain.session",
    "com.zxplat.plugin.chs.domain.settlement",
    "com.zxplat.plugin.chs.domain.invoke.entity"
})
@EnableJpaRepositories(basePackages = {
    "com.zxplat.plugin.chs.domain.audit.repository",
    "com.zxplat.plugin.chs.domain.catalog.repository",
    "com.zxplat.plugin.chs.domain.notice.repository",
    "com.zxplat.plugin.chs.domain.inpatient.repository",
    "com.zxplat.plugin.chs.domain.inventory.repository",
    "com.zxplat.plugin.chs.domain.outpatient.repository",
    "com.zxplat.plugin.chs.domain.platformdict.repository",
    "com.zxplat.plugin.chs.domain.reconciliation.repository",
    "com.zxplat.plugin.chs.domain.session.repository",
    "com.zxplat.plugin.chs.domain.settlement.repository",
    "com.zxplat.plugin.chs.domain.invoke.repository"
})
public class ChsModuleDomainAutoConfiguration {
}
