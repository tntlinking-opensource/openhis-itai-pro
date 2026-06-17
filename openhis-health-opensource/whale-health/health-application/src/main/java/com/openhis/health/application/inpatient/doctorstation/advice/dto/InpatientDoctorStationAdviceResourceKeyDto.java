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

package com.openhis.health.application.inpatient.doctorstation.advice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import com.openhis.health.domain.share.constants.DoctorStationAdviceConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-DOR-15/16：待签发或待签退资源键（与列表行 {@code productTableName} + {@code requestId} 对齐）。
 */
@Data
@Accessors(chain = true)
/** 住院医生站医嘱资源键。 */
@Schema(description = "住院医生站医嘱资源键")
public class InpatientDoctorStationAdviceResourceKeyDto {

    /** 产品定义表名（与暂存、列表、IF-IP-DOR-11 目录锚点一致）。 */
    @NotBlank(message = DoctorStationAdviceConstants.VALIDATION_PRODUCT_TABLE_NAME_REQUIRED)
    @Schema(
        description = "产品定义表名：med_medication_definition / wkf_activity_definition",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String productTableName;

    /** 药品/诊疗请求聚合主键。 */
    @NotNull(message = DoctorStationAdviceConstants.VALIDATION_REQUEST_ID_REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    /** 聚合主键。 */
    @Schema(description = "聚合主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long requestId;
}
