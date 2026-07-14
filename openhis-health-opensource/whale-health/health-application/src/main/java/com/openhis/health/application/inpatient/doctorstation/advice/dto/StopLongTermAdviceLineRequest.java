/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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

    /** 医嘱请求主键。 */
    @NotNull(message = DoctorStationAdviceConstants.VALIDATION_REQUEST_ID_REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "医嘱请求主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long requestId;

    /** 锚定产品定义物理表名。 */
    @NotBlank(message = DoctorStationAdviceConstants.VALIDATION_PRODUCT_TABLE_NAME_REQUIRED)
    /** 产品定义表名。 */
    @Schema(description = "产品定义表名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String productTableName;

    /** 停嘱时间。 */
    @Schema(description = "停嘱时间")
    private LocalDateTime stopTime;
}
