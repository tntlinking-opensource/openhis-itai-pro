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

package com.openhis.health.application.finance.priceadjustmentdetail.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 分页查询已生效调价项目主行请求（IF-PRC-DTL-02）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "分页查询调价明细请求")
public class PagePriceAdjustmentApprovedLineRequest extends AbstractHealthPageRequest {

    /** 生效时间下限（approve_time 闭区间，**BR-PRC-DTL-001**）。 */
    @NotNull(message = "生效时间下限不能为空")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "生效时间下限", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime approveTimeFrom;

    /** 生效时间上限（须 ≥ approveTimeFrom）。 */
    @NotNull(message = "生效时间上限不能为空")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "生效时间上限", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime approveTimeTo;

    /** 项目物理表；空表示四档并集（**BR-PRC-DTL-004**）。 */
    @Schema(description = "项目物理表 item_table")
    private String itemTable;

    /** 项目名称模糊关键字；与 itemCodeKeyword 双填时 AND（**BR-PRC-DTL-005**）。 */
    @Schema(description = "项目名称关键字")
    private String itemNameKeyword;

    /** 院内项目编码模糊关键字。 */
    @Schema(description = "院内项目编码关键字")
    private String itemCodeKeyword;

    /** 调价单号模糊关键字（**BR-PRC-DTL-006**）。 */
    @Schema(description = "调价单号")
    private String busNo;
}
