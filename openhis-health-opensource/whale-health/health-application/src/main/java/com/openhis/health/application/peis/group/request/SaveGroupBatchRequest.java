/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * 保存体检分组请求（GRP-03；原批次）。
 */
@Getter
@Setter
public class SaveGroupBatchRequest {

    /** 分组主键（空则新建）。 */
    private Long batchId;

    /** 单位主键。 */
    private Long companyId;

    /** 合同主键。 */
    private Long contractId;

    /** 分组名称。 */
    private String batchName;

    /** 本组可选套餐 AD 主键列表（至少一项）。 */
    private List<Long> packageActivityIds;

    /** 兼容：单个套餐 AD（缺省 list 时使用）。 */
    private Long packageActivityId;

    /** 预约日期（可选）。 */
    private LocalDate bookingDate;

    /** 体检中心机构主键（可选）。 */
    private Long orgId;
}
