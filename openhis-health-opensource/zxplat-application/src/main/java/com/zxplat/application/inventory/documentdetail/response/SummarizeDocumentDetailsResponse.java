/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.documentdetail.response;

import com.zxplat.application.inventory.documentdetail.dto.DocumentDetailItemSummaryRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 单据详情合计出参（IF-INV-DDC-{域}-03）。
 *
 * <p>页脚合计与按品规汇总行同源：同筛选条件下对 supply 明细全量拉取后聚合；无售价。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "单据详情合计 IF-INV-DDC-03")
public class SummarizeDocumentDetailsResponse {

    /** 金额合计（进价 totalPrice 代数加总）。 */
    @Schema(description = "金额合计 totalPriceSum")
    private BigDecimal totalPriceSum;

    /** 展示数量合计（操作单位 itemQuantity 加总；跨单位仅参考）。 */
    @Schema(description = "展示数量合计 itemQuantitySum")
    private BigDecimal itemQuantitySum;

    /** 命中明细行数（非品规桶数）。 */
    @Schema(description = "命中行数 rowCount")
    private long rowCount;

    /**
     * 按品规汇总行（同筛选；供汇总 Tab）；明细 Tab 可忽略。
     */
    @Schema(description = "按品规汇总行")
    private List<DocumentDetailItemSummaryRowDto> records;
}
