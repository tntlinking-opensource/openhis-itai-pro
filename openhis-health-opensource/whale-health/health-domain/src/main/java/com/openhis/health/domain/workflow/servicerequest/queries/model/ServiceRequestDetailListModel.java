/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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
