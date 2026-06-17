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

package com.openhis.health.module.common.assembler.advice.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 服务申请明细摘要（module-support 读模型）。
 *
 * <p>由 {@code ServiceApplyFormOrchestrationSupport#toDetailSummaryModels} 从
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
