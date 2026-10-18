/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.advice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.domain.share.constants.DoctorStationAdviceConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-DOR-25：取消停嘱批次行（长期医嘱）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院医生站-长期医嘱取消停嘱行")
public class CancelStopLongTermAdviceLineRequest {

    /** 医嘱请求主键。 */
    @NotNull(message = DoctorStationAdviceConstants.VALIDATION_REQUEST_ID_REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "医嘱请求主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long requestId;

    /** 产品定义表名。 */
    @NotBlank(message = DoctorStationAdviceConstants.VALIDATION_PRODUCT_TABLE_NAME_REQUIRED)
    @Schema(description = "产品定义表名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String productTableName;
}
