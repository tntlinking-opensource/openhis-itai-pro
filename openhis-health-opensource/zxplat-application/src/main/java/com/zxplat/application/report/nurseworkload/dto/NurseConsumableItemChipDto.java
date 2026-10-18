/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.nurseworkload.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 护士卡片耗材品名 chip（按 deviceDef + 展示名聚合）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "护士卡片耗材 chip")
public class NurseConsumableItemChipDto {

    /** 聚合键。 */
    @Schema(description = "聚合键")
    private String key;

    /** 展示名。 */
    @Schema(description = "展示名")
    private String label;

    /** 数量合计。 */
    @Schema(description = "数量合计")
    private BigDecimal quantity;
}
