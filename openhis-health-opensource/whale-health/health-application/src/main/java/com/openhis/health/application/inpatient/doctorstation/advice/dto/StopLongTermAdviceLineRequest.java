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

import java.time.LocalDateTime;

/**
 * IF-IP-DOR-20：停嘱批次行（长期医嘱 signOff + 停嘱时间）。
 *
 * <p>嵌于 {@link com.openhis.health.application.inpatient.doctorstation.advice.request.StopLongTermInpatientDoctorStationAdviceRequest}。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院医生站-长期医嘱停嘱行")
public class StopLongTermAdviceLineRequest {

    /** 药品/诊疗请求主键。 */
    @NotNull(message = DoctorStationAdviceConstants.VALIDATION_REQUEST_ID_REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "医嘱请求主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long requestId;

    /** 锚定产品定义物理表名。 */
    @NotBlank(message = DoctorStationAdviceConstants.VALIDATION_PRODUCT_TABLE_NAME_REQUIRED)
    @Schema(description = "产品定义表名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String productTableName;

    /** 停嘱时间；空则取当前时间。 */
    @Schema(description = "停嘱时间")
    private LocalDateTime stopTime;
}
