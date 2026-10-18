/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.bridge.adapter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zxplat.plugin.pacs.bridge.adapter.inbound.PacsAdapterReportFinalizeCommandHandler;
import com.zxplat.plugin.pacs.bridge.adapter.mapping.InMemoryPacsItemMapRepository;
import com.zxplat.plugin.pacs.bridge.adapter.outbound.AdapterPacsAdapterRuntimeSnapshotPort;
import com.zxplat.plugin.pacs.bridge.adapter.outbound.AdapterPacsNotifyReportPathPort;
import com.zxplat.plugin.pacs.bridge.adapter.outbound.MockPacsOutboundClient;
import com.zxplat.plugin.pacs.bridge.adapter.outbound.PacsNotifyReportPathCommandHandler;
import com.zxplat.plugin.pacs.bridge.adapter.outbound.PacsOutboundPort;
import com.zxplat.plugin.pacs.bridge.adapter.outbound.PacsOutboundRetryService;
import com.zxplat.plugin.pacs.bridge.adapter.outbound.PacsSendApplyCommandHandler;
import com.zxplat.domain.share.extension.pacsintegration.PacsAdapterRuntimeSnapshotPort;
import com.zxplat.domain.share.extension.pacsintegration.PacsNotifyReportPathPort;
import com.zxplat.plugin.pacs.domain.integrationlog.service.PacsIntegrationLogDomainService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * PACS adapter 自动装配（序号 15～20 国基 Mock 骨架）。
 *
 * <p>启用：{@code zxplat.modules.pacs.adapter.enabled=true}。</p>
 */
@AutoConfiguration
@ConditionalOnProperty(name = "zxplat.modules.pacs.adapter.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PacsAdapterProperties.class)
public class PacsAdapterAutoConfiguration {

    @Bean
    InMemoryPacsItemMapRepository inMemoryPacsItemMapRepository() {
        return new InMemoryPacsItemMapRepository();
    }

    @Bean
    PacsOutboundPort pacsOutboundPort(
        PacsAdapterProperties properties,
        InMemoryPacsItemMapRepository itemMapRepository) {
        return new MockPacsOutboundClient(properties, itemMapRepository);
    }

    @Bean
    PacsSendApplyCommandHandler pacsSendApplyCommandHandler(
        PacsOutboundPort outboundPort,
        PacsIntegrationLogDomainService integrationLogDomainService,
        ObjectMapper objectMapper) {
        return new PacsSendApplyCommandHandler(outboundPort, integrationLogDomainService, objectMapper);
    }

    @Bean
    PacsOutboundRetryService pacsOutboundRetryService(
        PacsSendApplyCommandHandler sendApplyCommandHandler,
        PacsIntegrationLogDomainService integrationLogDomainService) {
        return new PacsOutboundRetryService(sendApplyCommandHandler, integrationLogDomainService);
    }

    @Bean
    PacsAdapterReportFinalizeCommandHandler pacsAdapterReportFinalizeCommandHandler(
        PacsIntegrationLogDomainService integrationLogDomainService,
        ObjectMapper objectMapper) {
        return new PacsAdapterReportFinalizeCommandHandler(integrationLogDomainService, objectMapper);
    }

    @Bean
    PacsNotifyReportPathCommandHandler pacsNotifyReportPathCommandHandler(
        PacsOutboundPort outboundPort,
        PacsIntegrationLogDomainService integrationLogDomainService,
        ObjectMapper objectMapper) {
        return new PacsNotifyReportPathCommandHandler(outboundPort, integrationLogDomainService, objectMapper);
    }

    /**
     * 向应用层提供报告路径回传 Port（覆盖 NoOp）。
     *
     * @param handler adapter 编排
     * @return 已启用外发的端口
     */
    @Bean
    PacsNotifyReportPathPort pacsNotifyReportPathPort(PacsNotifyReportPathCommandHandler handler) {
        return new AdapterPacsNotifyReportPathPort(handler);
    }

    /**
     * 向应用层提供 adapter 运行快照 Port（覆盖 NoOp，供 CFG-04）。
     *
     * @param properties adapter 配置
     * @return 已装配的快照端口
     */
    @Bean
    PacsAdapterRuntimeSnapshotPort pacsAdapterRuntimeSnapshotPort(PacsAdapterProperties properties) {
        return new AdapterPacsAdapterRuntimeSnapshotPort(properties);
    }
}
