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

package com.openhis.health.domain.financial.cashiersettlement.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.CashierSettlementStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;

/**
 * 收费员交账头聚合根（只增不改）。
 */
@Getter
@Setter
@Entity
@Table(name = "fin_cashier_settlement")
public class CashierSettlement extends BaseAuditedEntity {

    /** 交账单号。 */
    @Column(name = "bus_no", nullable = false)
    private String busNo;

    /** 交账类型：OUTPATIENT / INPATIENT。 */
    @Column(name = "type_enum", nullable = false)
    private String typeEnum;

    /** 交账状态，本期固定 HANDED_OVER。 */
    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    /** 交账提交时刻。 */
    @Column(name = "settled_at", nullable = false)
    private LocalDateTime settledAt;

    /** 交账所属科室。 */
    @Column(name = "org_id", nullable = false)
    private Long orgId;

    /** 收费窗口，可选。 */
    @Column(name = "location_id")
    private Long locationId;

    /** 交账经办人。 */
    @Column(name = "cashier_id", nullable = false)
    private Long cashierId;

    /**
     * 初始化新建交账头为已交账归档态。
     *
     * @param settledAt 交账时刻
     */
    public void initHandedOver(LocalDateTime settledAt) {
        this.statusEnum = CashierSettlementStatus.HANDED_OVER.getCode();
        this.settledAt = settledAt;
    }

    /**
     * 断言交账单号非空。
     */
    public void assertBusNoNotBlank() {
        // 单号为空则无法作为交账归档凭证对外展示
        if (StringUtils.isBlank(this.busNo)) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "交账单号不能为空");
        }
    }
}
