/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.integration.pacs;

import java.util.List;

import com.zxplat.plugin.pacs.domain.port.PacsOrderIngressPort;

/**
 * 未装 / 未启用 pacs starter 时的空实现，保证医生站可注入 {@link PacsOrderIngressPort}。
 *
 * <p>由 {@link PacsOrderIngressPortAutoConfiguration} 注册，勿加 {@code @Component}
 * + {@code @ConditionalOnMissingBean}（扫描路径下该条件不可靠）。</p>
 */
public class NoOpPacsOrderIngressPort implements PacsOrderIngressPort {

    @Override
    public void assertActivitiesExecutable(List<Long> activityIds) {
        // NoOp：未装 PACS 时跳过模态能力断言
    }

    @Override
    public Long acceptSignedImagingServiceRequest(
            Long encounterId,
            Long patientId,
            Long serviceRequestId,
            Long fallbackOrganizationId,
            Long operatorId) {
        return null;
    }

    @Override
    public void withdrawSignedImagingServiceRequest(Long serviceRequestId, Long operatorId) {
        // NoOp：未装 PACS 时签退不联动工单
    }
}
