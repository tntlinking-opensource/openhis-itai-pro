/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.inventoryopening.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 库存期初库房下拉项（IF-BAS-INV-OPN-01）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "库存期初库房选项")
public class LocationOpeningOptionDto {

    /** 库房位置主键（adm_location.id）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "库房位置主键")
    private Long value;

    /** 库房名称。 */
    @Schema(description = "库房名称")
    private String label;

    /** 该库是否已关闭期初。 */
    @Schema(description = "是否已关闭期初")
    private Boolean openingClosed;

    /** 库房形态 LocationForm 持久化码。 */
    @Schema(description = "库房形态 formEnum")
    private String formEnum;
}
