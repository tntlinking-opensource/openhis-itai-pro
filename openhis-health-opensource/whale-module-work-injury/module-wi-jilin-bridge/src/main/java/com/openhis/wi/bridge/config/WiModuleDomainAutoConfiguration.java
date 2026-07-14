/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.bridge.config;

import com.openhis.wi.domain.repository.YbWiEncounterAnchorRepository;
import com.openhis.wi.domain.repository.YbWiInvokeSessionRepository;
import com.openhis.wi.domain.repository.YbWiPreSettleRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 工伤 domain JPA 扫描。
 *
 * <p>实体：工伤专属 + CHS invoke/settlement 共享表（只读实体扫描，不注册 CHS Repository）。</p>
 * <p>Repository：仅工伤专属；CHS 的 {@code YbInvokeLogRepository} /
 * {@code YbSettlement*Repository} 由 {@code ChsModuleDomainAutoConfiguration} 注册，
 * 二者同时 enabled 时切勿在本类再扫 {@code com.openhis.chs.**.repository}，否则会
 * BeanDefinitionOverrideException。</p>
 */
@Configuration
@ConditionalOnProperty(name = "openhis.modules.work-injury.enabled", havingValue = "true")
@ComponentScan(basePackages = "com.openhis.wi.domain")
@EntityScan(basePackages = {
    "com.openhis.wi.domain.entity",
    "com.openhis.chs.domain.invoke.entity",
    "com.openhis.chs.domain.settlement.entity"
})
@EnableJpaRepositories(basePackageClasses = {
    YbWiPreSettleRepository.class,
    YbWiInvokeSessionRepository.class,
    YbWiEncounterAnchorRepository.class
})
public class WiModuleDomainAutoConfiguration {
}
