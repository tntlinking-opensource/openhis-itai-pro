/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.inventoryopening.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 关闭库房期初请求（IF-BAS-INV-OPN-14）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "关闭期初", requiredProperties = {"locationId"})
public class CloseInventoryOpeningRequest {

    /** 目标库房主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "库房主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long locationId;
}
