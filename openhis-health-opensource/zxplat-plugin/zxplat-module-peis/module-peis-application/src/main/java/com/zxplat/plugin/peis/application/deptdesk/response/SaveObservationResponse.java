/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.deptdesk.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 保存观察实例响应。
 */
@Data
public class SaveObservationResponse {

    /** 观察实例主键。 */
    @Schema(description = "观察实例主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long observationId;

    /** 状态 code。 */
    @Schema(description = "状态")
    private String statusEnum;
}
