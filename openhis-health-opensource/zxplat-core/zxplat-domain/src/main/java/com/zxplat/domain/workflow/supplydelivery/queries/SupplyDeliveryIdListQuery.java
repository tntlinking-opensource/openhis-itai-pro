/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.supplydelivery.queries;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 按发放主键集合批量查询 {@link com.zxplat.domain.workflow.supplydelivery.entity.SupplyDelivery}。
 */
@Getter
@Setter
public class SupplyDeliveryIdListQuery {

    /** 发放记录主键 {@code wkf_supply_delivery.id} 列表。 */
    private List<Long> idList;
}
