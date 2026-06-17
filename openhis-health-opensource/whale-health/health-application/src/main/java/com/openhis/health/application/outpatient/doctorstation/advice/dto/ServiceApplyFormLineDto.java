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

package com.openhis.health.application.outpatient.doctorstation.advice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * IF-DOR-22 / IF-IP-DOR-24：申请单明细行摘要。
 */
@Data
@Accessors(chain = true)
@Schema(description = "申请单明细行摘要")
public class ServiceApplyFormLineDto {

    /** 服务申请主键 {@code wkf_service_request.id}。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "服务申请主键")
    private Long requestId;

    /** 诊疗活动定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "诊疗活动定义主键")
    private Long activityDefinitionId;

    /** 诊疗活动展示名。 */
    @Schema(description = "诊疗名称")
    private String activityName;

    /** 开立数量。 */
    @Schema(description = "数量")
    private BigDecimal quantity;

    /** 单位 code。 */
    @Schema(description = "单位 code")
    private String unitCode;

    /** 单位展示名。 */
    @Schema(description = "单位展示名")
    private String unitName;

    /** 纯备注，对应主表 {@code remark}。 */
    @Schema(description = "纯备注")
    private String remark;

    /** 部位/样本明细摘要。 */
    @Schema(description = "部位/样本明细")
    private List<ServiceRequestDetailSummaryDto> details;
}
