/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.bridge.config;

import com.zxplat.plugin.wi.domain.repository.YbWiEncounterAnchorRepository;
import com.zxplat.plugin.wi.domain.repository.YbWiInvokeSessionRepository;
import com.zxplat.plugin.wi.domain.repository.YbWiPreSettleRepository;
import com.zxplat.plugin.wi.domain.repository.YbWiReconDailyRepository;
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
 * 二者同时 enabled 时切勿在本类再扫 {@code com.zxplat.plugin.chs.**.repository}，否则会
 * BeanDefinitionOverrideException。</p>
 */
@Configuration
@ConditionalOnProperty(name = "zxplat.modules.work-injury.enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackages = "com.zxplat.plugin.wi.domain")
@EntityScan(basePackages = {
    "com.zxplat.plugin.wi.domain.entity",
    "com.zxplat.plugin.chs.domain.invoke.entity",
    "com.zxplat.plugin.chs.domain.settlement.entity"
})
@EnableJpaRepositories(basePackageClasses = {
    YbWiPreSettleRepository.class,
    YbWiInvokeSessionRepository.class,
    YbWiEncounterAnchorRepository.class,
    YbWiReconDailyRepository.class
})
public class WiModuleDomainAutoConfiguration {
}
