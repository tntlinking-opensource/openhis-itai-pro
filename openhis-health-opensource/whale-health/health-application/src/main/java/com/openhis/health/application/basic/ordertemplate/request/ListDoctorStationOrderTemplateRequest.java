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

package com.openhis.health.application.basic.ordertemplate.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-BAS-OTL-07：医生站可套用组套/医嘱模板列表请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站组套/医嘱模板列表请求")
public class ListDoctorStationOrderTemplateRequest {

    /** 当前就诊主键。 */
    @NotNull(message = "就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 当前患者主键。 */
    @NotNull(message = "患者标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 患者标识。 */
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /** 模板来源 Tab：MAINTAINED（组套）或 FROM_ADVICE（医嘱模板）。 */
    @NotBlank(message = "模板来源不能为空")
    /** 模板来源：MAINTAINED / FROM_ADVICE。 */
    @Schema(description = "模板来源：MAINTAINED / FROM_ADVICE", requiredMode = Schema.RequiredMode.REQUIRED)
    private String templateSourceEnum;

    /** 组套种类 Tab：GENERAL 或 TCM。 */
    @NotBlank(message = "组套种类不能为空")
    /** 组套种类：GENERAL / TCM。 */
    @Schema(description = "组套种类：GENERAL / TCM", requiredMode = Schema.RequiredMode.REQUIRED)
    private String templateKindEnum;

    /** 名称关键字（可选）。 */
    @Schema(description = "名称关键字")
    private String searchKey;
}
