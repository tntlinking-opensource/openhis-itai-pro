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

package com.openhis.health.application.outpatient.doctorstation.chinesemedicine.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * IF-DOR-36：中药处方暂存明细行请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "中药处方暂存明细行")
public class TcmRxDraftLineRequest {

    /** 行操作：CREATE / UPDATE / DELETE。 */
    @NotBlank(message = "行操作不能为空")
    @Schema(description = "行操作", requiredMode = Schema.RequiredMode.REQUIRED)
    private String lineOp;

    /** 药品申请主键（UPDATE/DELETE 必填）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "药品申请主键")
    private Long medicationRequestId;

    /** 饮片药品定义主键（CREATE/UPDATE 必填）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "饮片药品定义主键")
    private Long medicationDefinitionId;

    /** 单剂用量。 */
    @Schema(description = "单剂用量")
    private BigDecimal dose;

    /** 单剂用量单位编码。 */
    @Schema(description = "单剂用量单位编码")
    private String doseUnitCode;

    /** 味序。 */
    @Schema(description = "味序")
    private Integer sortNumber;

    /** 发药库位主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "发药库位主键")
    private Long dispenseLocationId;
}
