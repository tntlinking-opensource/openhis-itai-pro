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

package com.openhis.health.application.inventory.warehouse.gainloss.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class PageGainLosssRequest extends AbstractHealthPageRequest {

    /** 盘点药库；{@code 0} 表示当前用户可见全部药库。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 盘点药库 purposeLocationId。 */
    @Schema(description = "盘点药库 purposeLocationId，0 表示全部可见药库")
    private Long purposeLocationId;

    /** 单据状态 {@link com.openhis.health.domain.share.enums.SupplyStatus#getCode()}。 */
    @Schema(description = "状态 statusEnum")
    private String statusEnum;

    /** 申请时间起。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    /** 申请时间起。 */
    @Schema(description = "申请时间起")
    private LocalDateTime applyTimeFrom;

    /** 申请时间止。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    /** 申请时间止。 */
    @Schema(description = "申请时间止")
    private LocalDateTime applyTimeTo;
}
