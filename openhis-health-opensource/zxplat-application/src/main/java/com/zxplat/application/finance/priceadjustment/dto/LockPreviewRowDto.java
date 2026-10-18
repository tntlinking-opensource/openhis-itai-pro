/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.priceadjustment.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 提交锁预览行 DTO（IF-PRC-07）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "提交锁预览行")
public class LockPreviewRowDto {

    /** 库存项主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "库存项主键")
    private Long inventoryId;

    /** 物品表名。 */
    @Schema(description = "物品表名")
    private String itemTable;

    /** 物品定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "物品定义主键")
    private Long itemId;

    /** 库位主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "库位主键")
    private Long locationId;

    /** 批号。 */
    @Schema(description = "批号")
    private String lotNumber;

    /** 账面数量。 */
    @Schema(description = "账面数量")
    private BigDecimal quantity;

    /** 是否已被其它调价单锁定。 */
    @Schema(description = "是否已被其它调价单锁定")
    private Boolean lockedByOtherBusNo;

    /** 锁定方业务单号。 */
    @Schema(description = "锁定方业务单号")
    private String lockedBusNo;
}
