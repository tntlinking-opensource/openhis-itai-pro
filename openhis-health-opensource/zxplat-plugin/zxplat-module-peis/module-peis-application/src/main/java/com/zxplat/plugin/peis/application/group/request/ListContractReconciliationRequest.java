/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 团检合同对账查询请求。
 */
@Getter
@Setter
public class ListContractReconciliationRequest {

    /** 团检单位主键（必填）。 */
    private Long companyId;

    /** 合同支付状态筛选：UNPAID / PAID。 */
    private String payStatusCode;
}
