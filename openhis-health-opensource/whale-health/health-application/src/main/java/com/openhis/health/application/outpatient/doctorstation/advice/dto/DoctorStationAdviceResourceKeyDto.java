/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
    /** 聚合主键。 */
    @Schema(description = "聚合主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long requestId;
}
