/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.inventoryopening.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 关闭期初响应（IF-BAS-INV-OPN-14）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "关闭期初响应")
public class CloseInventoryOpeningResponse {

    /** 已关闭的库房主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "库房主键")
    private Long locationId;

    /** 关闭标志（成功恒为 true）。 */
    @Schema(description = "已关闭")
    private Boolean openingClosed;
}
