/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.supplydelivery.queries;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 按上游供应请求行主键集合批量查询发放记录（出库/入库发放等）。
 */
@Getter
@Setter
public class SupplyDeliveryRequestIdListQuery {

    /** 供应请求行主键 {@code request_id} 列表。 */
    private List<Long> requestIdList;
}
