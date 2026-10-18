/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.domain.port;

import java.util.List;

/**
 * HIS 医生站 → 自研 PACS/RIS 检查医嘱接单端口（AS-PACS-INT-001）。
 *
 * <p>实现落在 {@code module-pacs-application}；未装 pacs starter 时由 HIS 侧 NoOp 兜底。</p>
 */
public interface PacsOrderIngressPort {

    /**
     * IF-PACS-INT-07：断言 activity 均已配置启用态模态能力（缺失则拒绝）。
     *
     * @param activityIds ActivityDefinition 主键列表
     */
    void assertActivitiesExecutable(List<Long> activityIds);

    /**
     * IF-PACS-INT-01：若已签发的 ServiceRequest 为检查类，则创建（或幂等返回）检查工单。
     *
     * @param encounterId            就诊主键
     * @param patientId              患者主键
     * @param serviceRequestId       ServiceRequest 主键
     * @param fallbackOrganizationId 签发科室（SR.orgId 为空时回落）
     * @param operatorId             操作人
     * @return 工单主键；非检查类或 NoOp 时为 {@code null}
     */
    Long acceptSignedImagingServiceRequest(
            Long encounterId,
            Long patientId,
            Long serviceRequestId,
            Long fallbackOrganizationId,
            Long operatorId);

    /**
     * IF-PACS-INT-02：医生站签退后作废对应 orderLink；无有效 link 时整单作废。
     *
     * @param serviceRequestId ServiceRequest 主键
     * @param operatorId       操作人
     */
    void withdrawSignedImagingServiceRequest(Long serviceRequestId, Long operatorId);
}
