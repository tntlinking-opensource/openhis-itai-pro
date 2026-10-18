/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.medicaltech.execution.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 医技取消执行·耗材勾选项（AS-MT-EXE-001 IF-MT-EXE-05）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医技取消执行耗材项")
public class MedicalTechCancelDeviceItemDto {

    /** 待回退的发放行主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "发放行主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long dispenseId;
}
