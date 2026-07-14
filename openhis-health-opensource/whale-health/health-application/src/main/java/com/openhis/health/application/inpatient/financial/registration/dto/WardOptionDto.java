/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.registration.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 病区下拉选项 DTO。
 */
@Data
@Accessors(chain = true)
@Schema(description = "病区选项")
public class WardOptionDto {

    /** 病区位置 ID（业务字段）。 */
    @Schema(description = "病区位置 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long wardId;

    /** 病区编码。 */
    @Schema(description = "病区编码")
    private String wardCode;

    /** 病区名称。 */
    @Schema(description = "病区名称")
    private String wardName;
}
