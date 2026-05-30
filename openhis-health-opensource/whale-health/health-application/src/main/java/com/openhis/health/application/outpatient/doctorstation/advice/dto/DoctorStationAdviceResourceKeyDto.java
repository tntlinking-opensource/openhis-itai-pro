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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-DOR-15/16：待签发或待签退资源键。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站医嘱资源键")
public class DoctorStationAdviceResourceKeyDto {

    /** 产品定义表名（与暂存、列表、IF-DOR-11 目录锚点一致）。 */
    @NotBlank(message = "产品定义表名不能为空")
    @Schema(
        description = "产品定义表名：med_medication_definition / wkf_activity_definition / adm_device_definition",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String productTableName;

    /** 聚合主键。 */
    @NotNull(message = "请求主键不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "聚合主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long requestId;
}
