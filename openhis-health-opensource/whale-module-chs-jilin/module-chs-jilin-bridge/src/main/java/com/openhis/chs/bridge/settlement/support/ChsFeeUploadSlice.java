/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.settlement.support;

import com.openhis.health.domain.share.query.ProductDefinitionCatalogQueryModel;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 2204/2301 费用上传逻辑行（父 charge 或套餐子项展开行）。
 */
@Getter
@Setter
public class ChsFeeUploadSlice {

    /** 院内收费明细主键（套餐子项行仍挂父 chargeId）。 */
    private Long chargeItemId;

    /** 省平台费用明细流水号；套餐子项为 {@code 父feedetlSn.序号}（如 {@code CI….01}，≤30）。 */
    private String feedetlSn;

    /** 本行目录投影（子项或父项）。 */
    private ProductDefinitionCatalogQueryModel product;

    /** 行数量。 */
    private BigDecimal cnt;

    /** 行单价。 */
    private BigDecimal pric;

    /** 行金额。 */
    private BigDecimal detItemFeeSumamt;

    /** 缺码/合规提示用展示名。 */
    private String displayName;
}
