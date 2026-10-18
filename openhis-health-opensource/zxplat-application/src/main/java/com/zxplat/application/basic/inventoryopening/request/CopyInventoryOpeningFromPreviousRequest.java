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
 * 从上一张复制建草稿请求（IF-BAS-INV-OPN-16）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "复制期初单", requiredProperties = {"locationId"})
public class CopyInventoryOpeningFromPreviousRequest {

    /** 目标库房主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "库房主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long locationId;

    /**
     * 源单号（可选）。
     * <p>空：默认该库当前有效 AGREE；非空：须同库 OPENING 且状态为 AGREE 或 EXPIRED_INVALIDATED。</p>
     */
    @Schema(description = "源单号")
    private String sourceBusNo;
}
