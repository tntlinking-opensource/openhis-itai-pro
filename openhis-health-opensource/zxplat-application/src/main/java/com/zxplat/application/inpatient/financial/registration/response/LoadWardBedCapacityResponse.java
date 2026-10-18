/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.registration.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 病区床位余量响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "病区床位余量")
public class LoadWardBedCapacityResponse {

    /** 病区位置 ID（业务字段）。 */
    @Schema(description = "病区位置 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long wardId;

    /** 总床位数。 */
    @Schema(description = "总床位数")
    private Long totalBedCount;

    /** 未占用床位数。 */
    @Schema(description = "未占用床位数（启用床位且 operational_enum 非 IN_USE）")
    private Long vacantBedCount;
}
