/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.observation.command;

import lombok.Getter;
import lombok.Setter;

/**
 * PACS 检查摘要投影命令（DM-CLN-007 §6.3）。
 */
@Getter
@Setter
public class ProjectFromPacsSummaryCommand {

    /** 租户。 */
    private Long tenantId;

    /** 机构。 */
    private Long orgId;

    /** 就诊。 */
    private Long encounterId;

    /** 服务申请。 */
    private Long serviceRequestId;

    /** 检查项目 ActivityDefinition。 */
    private Long activityId;

    /** PACS 报告主键（写入 sourceObservationId 作溯源）。 */
    private Long pacsReportId;

    /** 结论文本（摘要）。 */
    private String conclusionText;

    /** 所见文本（可选，无结论时用）。 */
    private String findingsText;

    /** 操作人。 */
    private Long operatorId;
}
