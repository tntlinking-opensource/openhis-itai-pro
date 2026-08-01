/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * CHS domain 层 JPA 与 {@code *DomainService} 扫描配置。
 *
 * <p>注册 {@code com.openhis.chs.domain} 下实体、Repository 与领域服务 Bean，
 * 供 bridge Invoker 与 Orchestrator 注入使用。</p>
 */
@Configuration
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
@ComponentScan(basePackages = "com.openhis.chs.domain")
@EntityScan(basePackages = {
    "com.openhis.chs.domain.audit",
    "com.openhis.chs.domain.catalog",
    "com.openhis.chs.domain.notice",
    "com.openhis.chs.domain.inpatient",
    "com.openhis.chs.domain.inventory",
    "com.openhis.chs.domain.outpatient",
    "com.openhis.chs.domain.platformdict",
    "com.openhis.chs.domain.reconciliation",
    "com.openhis.chs.domain.session",
    "com.openhis.chs.domain.settlement",
    "com.openhis.chs.domain.invoke.entity"
})
@EnableJpaRepositories(basePackages = {
    "com.openhis.chs.domain.audit.repository",
    "com.openhis.chs.domain.catalog.repository",
    "com.openhis.chs.domain.notice.repository",
    "com.openhis.chs.domain.inpatient.repository",
    "com.openhis.chs.domain.inventory.repository",
    "com.openhis.chs.domain.outpatient.repository",
    "com.openhis.chs.domain.platformdict.repository",
    "com.openhis.chs.domain.reconciliation.repository",
    "com.openhis.chs.domain.session.repository",
    "com.openhis.chs.domain.settlement.repository",
    "com.openhis.chs.domain.invoke.repository"
})
public class ChsModuleDomainAutoConfiguration {
}
