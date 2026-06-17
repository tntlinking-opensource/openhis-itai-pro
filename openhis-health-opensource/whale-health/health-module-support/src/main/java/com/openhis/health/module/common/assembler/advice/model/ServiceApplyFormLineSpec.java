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
 * 申请单保存入参：主表一行（对应将创建的一条 {@code ServiceRequest} 草稿）。
 */
@Getter
@Setter
public class ServiceApplyFormLineSpec {

    /** 诊疗活动定义主键 {@code wkf_activity_definition.id}。 */
    private Long activityDefinitionId;

    /** 开立数量；须大于 0。 */
    private BigDecimal quantity;

    /** 计价单位 code（术语 {@code UNIT_CODE} 或产品槽位回落）。 */
    private String unitCode;

    /** 纯备注，落库 {@code wkf_service_request.remark}。 */
    private String remark;

    /** 部位/样本明细；检查/检验类别下由编排层校验至少一条。 */
    private List<ServiceApplyFormDetailItemSpec> detailItems;
}
