/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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

