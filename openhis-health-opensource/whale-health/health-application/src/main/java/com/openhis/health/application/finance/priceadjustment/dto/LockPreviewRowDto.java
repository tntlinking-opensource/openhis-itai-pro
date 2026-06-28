/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.finance.priceadjustment.dto;

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
