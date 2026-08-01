/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 保存团检批次请求（GRP-03）。
 */
@Getter
@Setter
public class SaveGroupBatchRequest {

    /** 批次主键（空则新建）。 */
    private Long batchId;

    /** 单位主键。 */
    private Long companyId;

    /** 合同主键。 */
    private Long contractId;

    /** 批次名称。 */
    private String batchName;

    /** 套餐 ActivityDefinition 主键（可选，缺省取合同）。 */
    private Long packageActivityId;

    /** 预约日期（可选）。 */
    private LocalDate bookingDate;

    /** 体检中心机构主键（可选）。 */
    private Long orgId;
}
