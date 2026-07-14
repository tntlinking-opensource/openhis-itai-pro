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

/**
 * IF-IP-DOR-15/16：待签发或待签退资源键（与列表行 {@code productTableName} + {@code requestId} 对齐）。
 */
@Data
@Accessors(chain = true)
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
