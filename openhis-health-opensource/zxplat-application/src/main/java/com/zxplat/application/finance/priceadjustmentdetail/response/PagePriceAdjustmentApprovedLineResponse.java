/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.priceadjustmentdetail.response;

import com.zxplat.application.finance.priceadjustmentdetail.dto.PriceAdjustmentApprovedLineListRowDto;
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

    /** 当前页主行。 */
    @Schema(description = "当前页主行")
    private List<PriceAdjustmentApprovedLineListRowDto> content;

    /** 当前页码。 */
    @Schema(description = "当前页码")
    private Integer pageNo;

    /** 页大小。 */
    @Schema(description = "页大小")
    private Integer pageSize;

    /** 总条数。 */
    @Schema(description = "总条数")
    private Long totalCount;
}
