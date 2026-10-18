/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.chargeitem.command;

import lombok.Getter;
import lombok.Setter;

import java.util.Collection;

/**
 * 按上游请求锚点冲销护士划价待结算收费明细（AS-IP-NUR-005 IF-IP-PRC-06 BR-IP-PRC-023/024/026）。
 *
 * <p>保留原正向待结算行（含反结再生正向），插入数量/金额取反的冲销行，{@code refund_id} 链回原行；不物理删除。
 * 冲销源判定见 {@code ChargeItem#isPricingOffsetLine()}（区分冲销负向 vs 再生血缘）。</p>
 */
@Getter
@Setter
public class OffsetNursePricingChargeItemsByServiceAnchorsCommand {

    /**
     * 操作人主键（审计/编排用；冲销负向 Charge <strong>不</strong>再写操作人列——
     * 操作人展示跟 Procedure CANCEL / DeviceDispense 退耗真源）。
     */
    private Long operatorId;

    /** 就诊标识。 */
    private Long encounterId;

    /** 上游业务表名（如 {@code wkf_service_request}、{@code wkf_device_request}）。 */
    private String serviceTable;

    /** 上游业务主键集合。 */
    private Collection<Long> serviceIds;
}
