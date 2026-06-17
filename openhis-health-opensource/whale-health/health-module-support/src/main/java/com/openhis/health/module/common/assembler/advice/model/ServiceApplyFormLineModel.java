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

import java.math.BigDecimal;
import java.util.List;

/**
 * 申请单列表读模型：单条 {@code ServiceRequest} 行摘要。
 */
@Getter
@Setter
public class ServiceApplyFormLineModel {

    /** 服务申请主键 {@code wkf_service_request.id}。 */
    private Long requestId;

    /** 诊疗活动定义主键。 */
    private Long activityDefinitionId;

    /** 诊疗活动展示名。 */
    private String activityName;

    /** 开立数量。 */
    private BigDecimal quantity;

    /** 单位 code。 */
    private String unitCode;

    /** 单位展示名。 */
    private String unitName;

    /** 纯备注（主表字段）。 */
    private String remark;

    /** 部位/样本明细摘要列表。 */
    private List<ServiceRequestDetailSummaryModel> details;
}
