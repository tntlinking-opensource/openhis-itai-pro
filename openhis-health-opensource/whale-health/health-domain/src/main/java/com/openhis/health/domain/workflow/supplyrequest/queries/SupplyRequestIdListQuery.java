/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.supplyrequest.queries;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 按主键集合批量加载供应请求行。
 */
@Getter
@Setter
public class SupplyRequestIdListQuery {

    /** 供应请求行主键列表 {@code wkf_supply_request.id}。 */
    private List<Long> idList;
}
