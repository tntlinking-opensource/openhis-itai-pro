/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.settlement.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 医保费用明细报表 SPI 明细分页出参（DB 分页，禁止全窗物化）。
 */
@Data
@Accessors(chain = true)
public class InsuranceSettlementFeeDetailPageExtensionOut {

    /** 当前页明细行。 */
    private List<InsuranceSettlementFeeDetailLineExtensionOut> content = new ArrayList<>();

    /** 同筛选命中总行数。 */
    private long totalCount;

    /** 请求页码（1-based）。 */
    private int pageNo;

    /** 请求页大小。 */
    private int pageSize;
}
