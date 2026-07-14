/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.supplydelivery.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 按供应请求维度汇总的发放读模型（请求行与发放记录对应及数量）。
 */
@Getter
@Setter
public class SupplyDeliveryByRequestModel {

    /** 供应请求行主键。 */
    private Long requestId;
    /** 发放记录主键。 */
    private Long deliveryId;
    /** 发放数量（可正可负依业务）。 */
    private BigDecimal quantity;
    /** 追溯码（汇总核发落库）。 */
    private String traceNo;
}
