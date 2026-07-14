/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.patient.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 更新患者请求：继承建档字段并强制携带患者主键。
 *
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UpdatePatientRequest extends CreatePatientRequest {

    /** 待更新的患者主键。 */
    @NotNull(message = "患者ID不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
}
