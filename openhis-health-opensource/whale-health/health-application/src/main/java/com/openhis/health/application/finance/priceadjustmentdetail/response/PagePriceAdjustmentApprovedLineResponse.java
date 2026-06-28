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

package com.openhis.health.application.finance.priceadjustmentdetail.response;

import com.openhis.health.application.finance.priceadjustmentdetail.dto.PriceAdjustmentApprovedLineListRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 分页查询调价明细响应（IF-PRC-DTL-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "分页查询调价明细响应")
public class PagePriceAdjustmentApprovedLineResponse {

    /** 当前页主行列表（不含调前/调后价与子表）。 */
    @Schema(description = "当前页主行")
    private List<PriceAdjustmentApprovedLineListRowDto> content;

    /** 当前页码（1-based）。 */
    @Schema(description = "当前页码")
    private Integer pageNo;

    /** 页大小。 */
    @Schema(description = "页大小")
    private Integer pageSize;

    /** 主行总条数。 */
    @Schema(description = "总条数")
    private Long totalCount;
}
