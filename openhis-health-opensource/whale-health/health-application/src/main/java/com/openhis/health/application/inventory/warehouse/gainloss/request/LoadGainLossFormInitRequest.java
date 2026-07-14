/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.gainloss.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 药库损益页初始化请求（IF-WHS-GL-01）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "药库损益页初始化 IF-WHS-GL-01")
public class LoadGainLossFormInitRequest {

    /** 兼容字段，服务端忽略。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "兼容字段，服务端忽略")
    private Long organizationId;
}
