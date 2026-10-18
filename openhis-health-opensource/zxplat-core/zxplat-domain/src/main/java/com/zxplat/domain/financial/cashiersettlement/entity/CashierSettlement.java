/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.financial.cashiersettlement.entity;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.enums.CashierSettlementStatus;
import com.zxplat.domain.share.enums.CashierSettlementType;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交账批次头聚合根（DM-FIN-003 v3.0）。
 *
 * <p>提交即 {@code SUBMITTED}；账在源表挂载。仅保留新列，不映射废止列。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "fin_cashier_settlement")
public class CashierSettlement extends BaseAuditedEntity {

    /** 交账单号。 */
    @Column(name = "bus_no", nullable = false)
    private String busNo;

    /** 交账类型：OP_PAYMENT / IP_PAYMENT / OP_PHARMACY / IP_PHARMACY。 */
    @Column(name = "type_enum", nullable = false)
    private String typeEnum;

    /** 审批状态：SUBMITTED / APPROVED / REJECTED / WITHDRAWN。 */
    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    /** 交账科室；收款必填，药房可空。 */
    @Column(name = "org_id")
    private Long orgId;

    /** 交账药房；药房必填，收款侧不用。 */
    @Column(name = "location_id")
    private Long locationId;

    /** 业务窗起（时分秒，提交落库）。 */
    @Column(name = "date_from")
    private LocalDateTime dateFrom;

    /** 业务窗止（时分秒，提交落库）。 */
    @Column(name = "date_to")
    private LocalDateTime dateTo;

    /** 挂载笔数快照（费用=payment；药房=dispense 行数）。 */
    @Column(name = "payment_count")
    private Integer paymentCount;

    /** 列表金额快照（口径见 DM §2.3.3）。 */
    @Column(name = "total_income", precision = 19, scale = 6)
    private BigDecimal totalIncome;

    /** 窗内人员快照（逗号分隔 Long）。 */
    @Column(name = "practitioner_ids", columnDefinition = "text")
    private String practitionerIds;

    /** 提交人。 */
    @Column(name = "submitter_id", nullable = false)
    private Long submitterId;

    /** 交账提交时间。 */
    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    /** 审批人（通过或驳回时写，供日结/审批列表展示）。 */
    @Column(name = "approver_id")
    private Long approverId;

    /** 审批时间（通过或驳回时刻）。 */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /**
     * 初始化新建交账头为待审态。
     *
     * @param submittedAt 提交时刻
     */
    public void initSubmitted(LocalDateTime submittedAt) {
        this.statusEnum = CashierSettlementStatus.SUBMITTED.getCode();
        this.submittedAt = submittedAt;
    }

    /**
     * 待审 → 撤回。
     *
     * @throws BusinessException 非待审
     */
    public void markWithdrawn() {
        this.assertSubmittedForWrite("撤回");
        this.statusEnum = CashierSettlementStatus.WITHDRAWN.getCode();
    }

    /**
     * 待审 → 驳回；写入驳回人与驳回时刻（复用审批人/审批时间列）。
     *
     * @param approverId 驳回人（财务审批人）
     * @param approvedAt 驳回时刻
     * @throws BusinessException 非待审或参数空
     */
    public void markRejected(Long approverId, LocalDateTime approvedAt) {
        this.assertSubmittedForWrite("驳回");
        // 驳回与通过同列展示审批人/时间，四类日结列表均依赖此回填
        if (approverId == null || approvedAt == null) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "审批人与审批时间不能为空");
        }
        this.statusEnum = CashierSettlementStatus.REJECTED.getCode();
        this.approverId = approverId;
        this.approvedAt = approvedAt;
    }

    /**
     * 待审 → 审批通过。
     *
     * @param approverId 审批人
     * @param approvedAt 审批时刻
     * @throws BusinessException 非待审或参数空
     */
    public void markApproved(Long approverId, LocalDateTime approvedAt) {
        this.assertSubmittedForWrite("审批通过");
        // 审批人与时间须成对写入，供审批列表展示
        if (approverId == null || approvedAt == null) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "审批人与审批时间不能为空");
        }
        this.statusEnum = CashierSettlementStatus.APPROVED.getCode();
        this.approverId = approverId;
        this.approvedAt = approvedAt;
    }

    /**
     * 断言交账单号非空。
     */
    public void assertBusNoNotBlank() {
        // 单号为空则无法作为交账凭证对外展示
        if (StringUtils.isEmpty(this.busNo)) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "交账单号不能为空");
        }
    }

    /**
     * 按类型断言范围键：收款须科室；药房须药房。
     *
     * @throws BusinessException 范围键缺失
     */
    public void assertPaymentScopeKeys() {
        // 药房日结范围键为 location，org 可空
        if (CashierSettlementType.isPharmacyType(this.typeEnum)) {
            if (this.locationId == null) {
                throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "药房日结须指定药房");
            }
            return;
        }
        // 收款日结范围键为科室，不绑款台
        if (this.orgId == null) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "收款日结须指定科室");
        }
    }

    /**
     * 断言当前为待审，否则拒绝写动作。
     *
     * @param actionLabel 动作中文名（用于提示）
     */
    private void assertSubmittedForWrite(String actionLabel) {
        // 仅 SUBMITTED 允许撤回/驳回/通过；已终态不可改
        if (!CashierSettlementStatus.isSubmitted(this.statusEnum)) {
            throw new BusinessException(
                ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_STATUS,
                "当前交账状态不允许" + actionLabel);
        }
    }
}
