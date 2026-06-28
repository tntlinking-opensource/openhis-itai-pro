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

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 调价明细页初始化响应（IF-PRC-DTL-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "调价明细页初始化响应")
public class InitPriceAdjustmentDetailResponse {

    /** 默认生效时间下限（当日 00:00:00）。 */
    @Schema(description = "默认生效时间下限")
    private String defaultApproveTimeFrom;

    /** 默认生效时间上限（当日 23:59:59）。 */
    @Schema(description = "默认生效时间上限")
    private String defaultApproveTimeTo;

    /** 四档 item_table Tab 选项。 */
    @Schema(description = "项目类型 Tab 选项")
    private List<OptionDto> itemTableTabOptions;

    /** 调价原因枚举选项。 */
    @Schema(description = "调价原因选项")
    private List<OptionDto> adjustmentReasonOptions;

    /** 默认分页大小。 */
    @Schema(description = "默认分页大小")
    private Integer defaultPageSize;
}
