/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.adapter.pacs.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.health.adapter.pacs.inbound.PacsAdapterReportFinalizeCommandHandler;
import com.openhis.health.adapter.pacs.mapping.InMemoryPacsItemMapRepository;
import com.openhis.health.adapter.pacs.outbound.AdapterPacsAdapterRuntimeSnapshotPort;
import com.openhis.health.adapter.pacs.outbound.AdapterPacsNotifyReportPathPort;
import com.openhis.health.adapter.pacs.outbound.MockPacsOutboundClient;
import com.openhis.health.adapter.pacs.outbound.PacsNotifyReportPathCommandHandler;
import com.openhis.health.adapter.pacs.outbound.PacsOutboundPort;
import com.openhis.health.adapter.pacs.outbound.PacsOutboundRetryService;
import com.openhis.health.adapter.pacs.outbound.PacsSendApplyCommandHandler;
import com.openhis.health.domain.share.extension.pacsintegration.PacsAdapterRuntimeSnapshotPort;
import com.openhis.health.domain.share.extension.pacsintegration.PacsNotifyReportPathPort;
import com.openhis.health.domain.pacs.integrationlog.service.PacsIntegrationLogDomainService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * PACS adapter 自动装配（序号 15～20 国基 Mock 骨架）。
 *
 * <p>启用：{@code openhis.modules.pacs.adapter.enabled=true}。</p>
 */
@AutoConfiguration
@ConditionalOnProperty(name = "openhis.modules.pacs.adapter.enabled", havingValue = "true")
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
