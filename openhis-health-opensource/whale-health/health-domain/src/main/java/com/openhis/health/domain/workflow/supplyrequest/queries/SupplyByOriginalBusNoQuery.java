/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.supplyrequest.queries;

import lombok.Getter;
import lombok.Setter;

/**
 * 按来源/原始业务单号查询供应请求（如进货关联订货、退库关联原单等）。
 */
@Getter
@Setter
public class SupplyByOriginalBusNoQuery {

    /** 原始业务单号 {@code wkf_supply_request.original_bus_no}。 */
    private String originalBusNo;
}
