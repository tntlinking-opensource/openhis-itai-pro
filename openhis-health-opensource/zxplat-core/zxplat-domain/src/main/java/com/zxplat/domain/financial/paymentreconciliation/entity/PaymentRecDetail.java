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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付流水明细。
 */
@Getter
@Setter
@Entity
@Table(name = "fin_payment_rec_detail")
public class PaymentRecDetail extends BaseAuditedEntity {
    @Column(name = "reconciliation_id", nullable = false)
    /** 对账主键。 */
    private Long reconciliationId;
    @Column(name = "predecessor_id")
    /** 前驱主键。 */
    private Long predecessorId;
    @Column(name = "target_enum")
    /** 目标枚举编码。 */
    private String targetEnum;
    @Column(name = "account_id", nullable = false)
    /** 账户主键。 */
    private Long accountId;
    @Column(name = "account_code")
    /** 账户编码。 */
    private String accountCode;
    @Column(name = "pay_enum", nullable = false)
    /** 支付枚举编码。 */
    private String payEnum;
    @Column(name = "pay_level_enum")
    /** 支付等级枚举编码。 */
    private String payLevelEnum;
    @Column(name = "amount", nullable = false, precision = 19, scale = 6)
    /** 金额（元）。 */
    private BigDecimal amount;
    @Column(name = "return_amount", precision = 19, scale = 6)
    /** 退回金额。 */
    private BigDecimal returnAmount;
    @Column(name = "charge_amount", precision = 19, scale = 6)
    /** 收费金额。 */
    private BigDecimal chargeAmount;
    @Column(name = "pay_trans_no")
    /** 支付交易号。 */
    private String payTransNo;
    @Column(name = "pay_trans_text")
    /** 支付交易文本。 */
    private String payTransText;
    @Column(name = "pay_trans_date")
    /** 支付交易日期。 */
    private LocalDateTime payTransDate;
    @Column(name = "before_balance", precision = 19, scale = 6)
    /** 之前balance。 */
    private BigDecimal beforeBalance;
    @Column(name = "after_balance", precision = 19, scale = 6)
    /** 之后balance。 */
    private BigDecimal afterBalance;
    @Column(name = "result_enum", nullable = false)
    /** 结果枚举编码。 */
    private String resultEnum;

    /**
     * 断言本条明细在插入前已满足 {@code fin_payment_rec_detail} 非空列（对账主键、账户、渠道、金额、结果枚举）。
     *
     * <p>由领域服务在内存装配完一行后、批量 {@code saveAll} 之前调用。</p>
     *
     * @throws BusinessException 任一非空列未赋值
     */
    public void assertPersistableChildRow() {
        if (this.reconciliationId == null) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT, "支付明细对账主键不能为空");
        }
        if (this.accountId == null) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT, "支付明细账户标识不能为空");
        }
        if (StringUtils.isEmpty(this.payEnum)) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT, "支付明细支付渠道枚举不能为空");
        }
        if (this.amount == null) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT, "支付明细金额不能为空");
        }
        if (StringUtils.isEmpty(this.resultEnum)) {
            throw new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT, "支付明细结果枚举不能为空");
        }
    }
}
