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

package com.openhis.health.application.finance.priceadjustment.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 价格调整页初始化响应（IF-PRC-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "价格调整页初始化响应")
public class InitPriceAdjustmentResponse {

    /** 调价原因下拉。 */
    @Schema(description = "调价原因下拉")
    private List<OptionDto> adjustmentReasonOptions;

    /** 审批状态筛选项。 */
    @Schema(description = "审批状态筛选项")
    private List<OptionDto> statusFilterOptions;

    /** 项目表 Tab 选项（value=物理表名）。 */
    @Schema(description = "项目表 Tab 选项")
    private List<OptionDto> itemTableTabOptions;

    /** 医嘱定价来源租户策略（非阻断；缺省按批号价）。 */
    @Schema(description = "医嘱定价来源策略")
    private String orderPricingSource;
}
