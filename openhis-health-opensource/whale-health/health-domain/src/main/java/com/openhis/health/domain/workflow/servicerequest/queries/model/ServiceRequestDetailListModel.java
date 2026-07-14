/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.servicerequest.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 服务申请明细列表行读模型（部位/样本等 target 引用 + 展示名）。
 *
 * <p>供医生站申请单列表、医嘱统一列表等批量回显；不含原始 JSON。</p>
 */
@Getter
@Setter
public class ServiceRequestDetailListModel {

    /** 明细主键。 */
    private Long id;

    /** 所属服务申请主键。 */
    private Long serviceReqId;

    /** 目标表名（如 {@code dia_body_structure}、{@code adm_specimen_definition}）。 */
    private String targetTable;

    /** 目标表主键。 */
    private Long targetId;

    /** 目标主数据展示名（由读侧按 targetTable 批量解析；未命中时为 null）。 */
    private String targetDisplayName;

    /** 与该目标同行的补充说明（非纯备注行）。 */
    private String remark;
}
