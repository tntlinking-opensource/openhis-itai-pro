/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.priceadjustment.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 物流调价在途预检结果 DTO（IF-PRC-08）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "在途预检结果")
public class InTransitCheckDto {

    /** 是否阻断提交。 */
    @Schema(description = "是否阻断提交")
    private Boolean blocked;

    /** 原因码。 */
    @Schema(description = "原因码")
    private String reasonCode;

    /** 阻断说明。 */
    @Schema(description = "阻断说明")
    private String reasonMessage;

    /** 阻断项目表名。 */
    @Schema(description = "阻断项目表名")
    private String itemTable;

    /** 阻断项目主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "阻断项目主键")
    private Long itemId;
}
