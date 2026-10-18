/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.financial.paymentreconciliation.entity;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.zxplat.framework.core.application.util.StringUtils;

/**
 * 支付流水关联的医保正式结算凭证（一行一个 {@code setl_id}）。
 */
@Getter
@Setter
@Entity
@Table(name = "fin_payment_yb_settlement")
public class PaymentYbSettlement extends BaseAuditedEntity {

    @Column(name = "reconciliation_id", nullable = false)
    private Long reconciliationId;

    @Column(name = "setl_id", nullable = false, length = 30)
    private String setlId;

    @Column(name = "chrg_bchno", length = 30)
    private String chrgBchno;

    @Column(name = "mdtrt_id", length = 30)
    private String mdtrtId;

    @Column(name = "settlement_record_id")
    private Long settlementRecordId;

    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    public void assertPersistableChildRow() {
        if (this.reconciliationId == null) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT, "医保结算关联对账主键不能为空");
        }
        if (StringUtils.isEmpty(this.setlId)) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT, "医保 setl_id 不能为空");
        }
        if (StringUtils.isEmpty(this.status)) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT, "医保结算关联状态不能为空");
        }
    }
}
