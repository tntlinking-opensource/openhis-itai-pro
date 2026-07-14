/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.chargeitem.command;

import lombok.Getter;
import lombok.Setter;

import java.util.Collection;

/**
 * 按上游请求锚点逻辑删除护士划价待结算收费明细（AS-IP-NUR-005 IF-IP-PRC-06 BR-IP-PRC-023/026）。
 */
@Getter
@Setter
public class VoidNursePricingChargeItemsByServiceAnchorsCommand {

    /** 操作人主键。 */
    private Long operatorId;

    /** 就诊标识。 */
    private Long encounterId;

    /** 上游业务表名（如 {@code wkf_service_request}、{@code wkf_device_request}）。 */
    private String serviceTable;

    /** 上游业务主键集合。 */
    private Collection<Long> serviceIds;
}
