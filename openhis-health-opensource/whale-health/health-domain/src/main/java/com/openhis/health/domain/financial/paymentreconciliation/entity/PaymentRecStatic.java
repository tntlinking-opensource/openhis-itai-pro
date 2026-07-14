/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.financial.paymentreconciliation.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 支付分类统计快照。
 */
@Getter
@Setter
@Entity
@Table(name = "fin_payment_rec_static")
public class PaymentRecStatic extends BaseAuditedEntity {
    @Column(name = "reconciliation_id", nullable = false)
    /** 对账主键。 */
    private Long reconciliationId;
    /** 汇总口径编码（库表 int4，非枚举 code 字符串）。 */
    @Column(name = "method_code", nullable = false)
    private String methodCode;
    @Column(name = "type_code", nullable = false)
    /** 类型编码。 */
    private String typeCode;
    @Column(name = "type_name")
    /** 类型名称。 */
    private String typeName;
    @Column(name = "amount", nullable = false, precision = 19, scale = 6)
    /** 金额（元）。 */
    private BigDecimal amount;
    @Column(name = "charge_item_ids")
    /** 收费项标识集合。 */
    private String chargeItemIds;
}
