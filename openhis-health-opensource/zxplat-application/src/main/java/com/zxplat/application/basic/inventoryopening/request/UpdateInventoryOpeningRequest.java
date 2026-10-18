/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.inventoryopening.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.basic.inventoryopening.dto.InventoryOpeningLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 编辑期初单请求（IF-BAS-INV-OPN-05）；明细全量替换。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "编辑期初单", requiredProperties = {"busNo", "locationId", "occurrenceTime"})
public class UpdateInventoryOpeningRequest {

    /** 既有业务单号。 */
    @Schema(description = "单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;

    /** 目标库房主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "库房主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long locationId;

    /** 经手人（可选）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "经手人")
    private Long practitionerId;

    /** 业务发生时点。 */
    @Schema(description = "业务时点", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime occurrenceTime;

    /** 全量替换明细；可空表示清空为无行草稿。 */
    @Schema(description = "明细行")
    private List<InventoryOpeningLineDto> lines;
}
