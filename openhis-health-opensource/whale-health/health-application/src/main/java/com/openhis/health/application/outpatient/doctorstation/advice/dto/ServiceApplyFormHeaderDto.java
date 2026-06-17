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

import java.util.List;

/**
 * IF-DOR-22 / IF-IP-DOR-24：申请单头摘要（按 {@code prescriptionNo} 分组）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "申请单头摘要")
public class ServiceApplyFormHeaderDto {

    /** 申请单号（{@code AR} 前缀，同号下多行共享）。 */
    @Schema(description = "申请单号（AR 前缀）")
    private String prescriptionNo;

    /** 申请单类别 code（检查 23 / 检验 22 / 治疗 21）。 */
    @Schema(description = "申请单类别 code")
    private String categoryEnum;

    /** 整单执行科室 {@code adm_location.id}。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "整单执行科室")
    private Long orgId;

    /** 执行科室展示名。 */
    @Schema(description = "执行科室展示名")
    private String orgName;

    /** 组内首行开立时间，ISO-8601 local 字符串。 */
    @Schema(description = "首行开立时间 ISO-8601 local")
    private String authoredAt;

    /** 组内状态摘要（如全草稿、已签发）。 */
    @Schema(description = "组内状态摘要")
    private String statusSummary;

    /** 本申请单下的 ServiceRequest 行摘要。 */
    @Schema(description = "明细行")
    private List<ServiceApplyFormLineDto> lines;
}
