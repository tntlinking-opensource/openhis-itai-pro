/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.supplyrequest.queries;

import lombok.Getter;
import lombok.Setter;

/**
 * 按业务单号精确查询供应请求（同 {@code bus_no} 可能多行，一行一条明细）。
 *
 * <p>入参 {@code busNo} 由读服务 {@link com.zxplat.domain.workflow.supplyrequest.service.SupplyRequestQueryService#getSupplyByBusNo} 侧 {@code trim}。</p>
 */
@Getter
@Setter
public class SupplyByBusNoQuery {

    /** 业务单号 {@code wkf_supply_request.bus_no}。 */
    private String busNo;
}
