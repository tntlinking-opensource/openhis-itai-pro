/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.advice.support;

import lombok.Getter;
import lombok.Setter;

/**
 * 服务申请明细摘要（module-support 读模型）。
 *
 * <p>由 {@code ServiceApplyFormOrchestrator#toDetailSummaryModels} 从
 * {@code ServiceRequestDetailListModel} 映射；应用层再转为
 * {@code ServiceRequestDetailSummaryDto}。</p>
 */
@Getter
@Setter
public class ServiceRequestDetailSummaryModel {

    /** 目标表名（部位或样本定义表）。 */
    private String targetTable;

    /** 目标主键。 */
    private Long targetId;

    /** 目标主数据展示名；读侧批量解析，未命中时为 null。 */
    private String targetDisplayName;

    /** 与该目标同行的补充说明。 */
    private String remark;
}
