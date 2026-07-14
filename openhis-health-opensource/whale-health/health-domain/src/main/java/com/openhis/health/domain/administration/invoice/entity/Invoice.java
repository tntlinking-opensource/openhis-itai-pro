/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.invoice.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.InvoiceStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.openhis.whale.core.application.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 票据聚合根：草稿开具等写侧由 {@link com.openhis.health.domain.administration.invoice.service.InvoiceDomainService} 编排。
 */
@Getter
@Setter
@Entity
@Table(name = "adm_invoice")
public class Invoice extends BaseAuditedEntity {
    /** patient 标识。 */
    @Column(name = "patient_id")
    private Long patientId;
    /** reconciliation 标识。 */
    @Column(name = "reconciliation_id")
    private Long reconciliationId;
    /** payment 标识。 */
    @Column(name = "payment_id")
    private Long paymentId;
    /** 业务流水号（新建必填）。 */
    @Column(name = "bus_no", nullable = false)
    private String busNo;
    /** bill 编号。 */
    @Column(name = "bill_no")
    private String billNo;
    /** type 代码。 */
    @Column(name = "type_code")
    private String typeCode;
    /** cancelledReason。 */
    @Column(name = "cancelled_reason")
    private String cancelledReason;
    /** chargeItemIds。 */
    @Column(name = "charge_item_ids")
    private String chargeItemIds;
    /** paymentTerms。 */
    @Column(name = "payment_terms")
    private String paymentTerms;
    /** 金额。 */
    @Column(name = "amount")
    private BigDecimal amount;
    /** status 枚举值。 */
    @Column(name = "status_enum")
    private String statusEnum;
    /** issued 时间。 */
    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    /**
     * 断言草稿行在首次落库前已具备映射层要求的业务号与状态码（与 {@link InvoiceStatus#DRAFT} 等路径配合）。
     *
     * @throws BusinessException 业务号或状态未赋值
     */
    public void assertPersistableDraftRow() {
        if (StringUtils.isEmpty(this.busNo)) {
            throw new BusinessException(ErrorCodeConstants.INVOICE_INVALID_ARGUMENT, "票据业务流水号不能为空");
        }
        if (StringUtils.isEmpty(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.INVOICE_INVALID_ARGUMENT, "票据状态枚举不能为空");
        }
    }

    /**
     * 断言当前票据允许冲红（仅 ISSUED 状态允许）。
     *
     * <p>冲红语义：ISSUED → CANCELLED；草稿/已取消状态不允许冲红。</p>
     */
    public void assertWriteoffable() {
        if (!InvoiceStatus.ISSUED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.INVOICE_INVALID_STATUS, "仅已开具状态的票据允许冲红");
        }
    }

    /**
     * 断言当前票据允许作废（仅 DRAFT 状态允许）。
     *
     * <p>作废语义：DRAFT → CANCELLED；已开具/已取消状态不允许直接作废，需走冲红流程。</p>
     */
    public void assertVoidable() {
        if (!InvoiceStatus.DRAFT.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.INVOICE_INVALID_STATUS, "仅草稿状态的票据允许作废");
        }
    }

    /**
     * 将票据状态标记为已取消，并记录取消原因。
     *
     * @param reason 取消原因（冲红或作废时传入）
     */
    public void markCancelled(String reason) {
        this.statusEnum = InvoiceStatus.CANCELLED.getCode();
        this.cancelledReason = reason;
    }
}
