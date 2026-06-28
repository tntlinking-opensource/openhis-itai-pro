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

    /** 用户可读说明。 */
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
