/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.chargeitem.entity;

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
 * 收费明细聚合根（DM-ADM-011 §3.1，表 adm_charge_item）。
 */
@Getter
@Setter
@Entity
@Table(name = "adm_charge_item")
public class ChargeItem extends BaseAuditedEntity {

    /** 收费明细状态存储码（DM status_enum）。 */
    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    /** 生成来源存储码（DM generate_source_enum）。 */
    @Column(name = "generate_source_enum", nullable = false)
    private String generateSourceEnum;

    /** 收费上下文存储码（DM context_enum）。 */
    @Column(name = "context_enum", nullable = false)
    private String contextEnum;

    /** 业务流水号（DM bus_no）。 */
    @Column(name = "bus_no")
    private String busNo;

    /** 父收费明细主键（拆分/关联场景，DM parent_id）。 */
    @Column(name = "parent_id")
    private Long parentId;

    /** 结算快照处方号（DM prescription_no）：非业务真源；门诊药品以 MedicationRequest 为准，结算时抄写。 */
    @Column(name = "prescription_no")
    private String prescriptionNo;

    /** 患者主键（DM patient_id）。 */
    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    /** 就诊主键（DM encounter_id）。 */
    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    /** 费用发生时刻（DM occurrence_time）。 */
    @Column(name = "occurrence_time", nullable = false)
    private LocalDateTime occurrenceTime;

    /**
     * 医技确费人主键（DM fee_confirmer_id）。
     *
     * <p>住院医技费用清单一键确费时批量写入；空=未确费。临床执行人真源在 Procedure / DeviceDispense，
     * <strong>禁止</strong>再把本列当执行人或收款人——收款人见 {@link #entererId}。</p>
     */
    @Column(name = "fee_confirmer_id")
    private Long feeConfirmerId;

    /**
     * 医技确费时刻（DM fee_confirmed_at）。
     *
     * <p>与 {@link #feeConfirmerId} 成对；有值即该账单行已确费。</p>
     */
    @Column(name = "fee_confirmed_at")
    private LocalDateTime feeConfirmedAt;

    /** 申请机构主键（DM requesting_org_id）。 */
    @Column(name = "requesting_org_id")
    private Long requestingOrgId;

    /** 成本归属机构主键（DM cost_org_id）。 */
    @Column(name = "cost_org_id")
    private Long costOrgId;

    /** 数量值（DM quantity_value）。 */
    @Column(name = "quantity_value", nullable = false)
    private BigDecimal quantityValue;

    /** 数量单位（DM quantity_unit）。 */
    @Column(name = "quantity_unit", nullable = false)
    private String quantityUnit;

    /** 单价（DM unit_price）。 */
    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    /** 总价（DM total_price）。 */
    @Column(name = "total_price", nullable = false)
    private BigDecimal totalPrice;

    /** 收费项目定义主键（DM definition_id）。 */
    @Column(name = "definition_id", nullable = false)
    private Long definitionId;

    /** 定义明细主键（DM def_detail_id）。 */
    @Column(name = "def_detail_id")
    private Long defDetailId;

    /** 基准金额（DM base_amount）。 */
    @Column(name = "base_amount", nullable = false)
    private BigDecimal baseAmount;

    /** 折扣金额（DM discount_amount）。 */
    @Column(name = "discount_amount", nullable = false)
    private BigDecimal discountAmount;

    /** 附加费金额（DM surcharge_amount）。 */
    @Column(name = "surcharge_amount", nullable = false)
    private BigDecimal surchargeAmount;

    /** 系统折后价（DM system_discount_price）。 */
    @Column(name = "system_discount_price")
    private BigDecimal systemDiscountPrice;

    /** 手工调价后金额（DM manual_adjusted_price）。 */
    @Column(name = "manual_adjusted_price")
    private BigDecimal manualAdjustedPrice;

    /**
     * 结算折扣率快照（DM settle_discount_rate）：折后占原价 (0,1]；未打折为空。
     */
    @Column(name = "settle_discount_rate")
    private BigDecimal settleDiscountRate;

    /**
     * 结算折扣来源（DM settle_discount_source_enum）：NONE / CONFIG / CASHIER。
     */
    @Column(name = "settle_discount_source_enum")
    private String settleDiscountSourceEnum;

    /** 改价原因码（DM override_reason_code）。 */
    @Column(name = "override_reason_code")
    private String overrideReasonCode;

    /** 改价原因说明（DM override_reason_text）。 */
    @Column(name = "override_reason_text")
    private String overrideReasonText;

    /**
     * 收费人主键（DM enterer_id = {@code adm_practitioner.id}）。
     *
     * <p>仅结算/退费完成时写入；开立时为空。开单医生见上游 Request {@code requester_id}。</p>
     */
    @Column(name = "enterer_id")
    private Long entererId;

    /**
     * 收费时间（DM entered_date）。
     *
     * <p>与支付单 {@code bill_date} 同值；开立时刻见上游 Request {@code req_opened_time}。</p>
     */
    @Column(name = "entered_date")
    private LocalDateTime enteredDate;

    /** 上游业务表名锚点（BR-CHG-001，DM service_table）。 */
    @Column(name = "service_table", nullable = false)
    private String serviceTable;

    /** 上游业务主键锚点（DM service_id）。 */
    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    /** 产品来源表名（DM product_table）。 */
    @Column(name = "product_table")
    private String productTable;

    /** 产品主键（DM product_id）。 */
    @Column(name = "product_id")
    private Long productId;

    /** 处置/手术类关联主键（DM procedure_id）。 */
    @Column(name = "procedure_id")
    private Long procedureId;

    /** 发药来源表名（DM dispense_table）。 */
    @Column(name = "dispense_table")
    private String dispenseTable;

    /** 发药主键（DM dispense_id）。 */
    @Column(name = "dispense_id")
    private Long dispenseId;

    /** 理赔状态枚举（DM claim_state_enum）。 */
    @Column(name = "claim_state_enum")
    private Integer claimStateEnum;

    /** 打印次数（DM print_count）。 */
    @Column(name = "print_count")
    private Integer printCount;

    /** 账户主键（DM account_id）。 */
    @Column(name = "account_id")
    private Long accountId;

    /** 开立机构主键（DM org_id）。 */
    @Column(name = "org_id")
    private Long orgId;

    /**
     * 退费/冲销/再生关联主键（DM refund_id）。
     *
     * <p>同字段承载两类关系，须结合数量符号区分：
     * <ul>
     *   <li>冲销负向行：数量非正，{@code refund_id} 指向被冲的正向源行；</li>
     *   <li>反结/半退再生正向行：数量为正，{@code refund_id} 指向源 {@code SETTLED} 行（血缘，非已冲销）。</li>
     * </ul>
     * 判定冲销留痕请用 {@link #isPricingOffsetLine()}，禁止仅凭 {@code refund_id != null} 排除正向再生行。</p>
     */
    @Column(name = "refund_id")
    private Long refundId;

    /**
     * 绑定就诊诊断主键有序串（有序；首位为主诊断 {@code adm_encounter_diagnosis.id}；挂费自请求拷贝）。
     */
    @Column(name = "encounter_diagnosis_ids", length = 2000)
    private String encounterDiagnosisIds;

    /** 是否中医相关标记（DM tcm_flag）。 */
    @Column(name = "tcm_flag")
    private Boolean tcmFlag;

    /**
     * 断言新建行满足落库前最小一致性与 DM 必填项（由领域服务在赋值完成后调用）。
     */
    public void assertPersistableNewRow() {
        if (this.patientId == null) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "收费明细患者标识不能为空");
        }
        if (this.encounterId == null) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "收费明细就诊标识不能为空");
        }
        if (StringUtils.isEmpty(this.busNo)) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "收费明细业务流水号不能为空");
        }
        if (StringUtils.isEmpty(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "收费明细状态枚举不能为空");
        }
        if (StringUtils.isEmpty(this.generateSourceEnum)) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "收费明细生成来源不能为空");
        }
        if (StringUtils.isEmpty(this.contextEnum)) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "收费明细收费上下文不能为空");
        }
        if (this.occurrenceTime == null) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "收费明细费用发生时间不能为空");
        }
        if (this.definitionId == null) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "收费明细项目定义标识不能为空");
        }
        if (this.totalPrice == null) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "收费明细总价不能为空");
        }
        if (this.quantityValue == null || StringUtils.isEmpty(this.quantityUnit)) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "收费明细数量及单位不能为空");
        }
        if (this.unitPrice == null || this.baseAmount == null || this.discountAmount == null || this.surchargeAmount == null) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "收费明细金额字段不完整");
        }
        if (StringUtils.isEmpty(this.serviceTable) || this.serviceId == null) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "收费明细业务来源锚点不能为空");
        }
        if (this.accountId == null) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "收费明细账户标识不能为空");
        }
        if (this.orgId == null) {
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "收费明细机构标识不能为空");
        }
    }

    /**
     * 是否划价冲销负向留痕行（数量非正且 {@code refund_id} 链回被冲源行）。
     *
     * <p>与反结/半退再生正向行区分：后者亦写 {@code refund_id} 指向源 {@code SETTLED}，但数量为正，
     * 仍可作为再次冲销源（DM-ADM-011 §6.1.3a / §6.1.12a）。</p>
     *
     * @return true=冲销负向留痕行；false=无 refund 链或为再生/普通正向行
     */
    public boolean isPricingOffsetLine() {
        if (this.refundId == null) {
            return false;
        }
        if (this.quantityValue == null) {
            return false;
        }
        // 数量非正：冲销负向事实（含异常零量冲销残留）
        return this.quantityValue.signum() <= 0;
    }

    /**
     * 写入收费折扣结算快照（BR-OPC-029/032 / BR-IP-STL-022/025）。
     *
     * <p>{@code discountAmount}=折后应收；{@code systemDiscountPrice}=优惠额（原价−折后）；
     * {@code totalPrice}/{@code baseAmount} 保持原价不变。</p>
     *
     * @param discountedReceivable 折后应收（结算 2 位）
     * @param discountGap          优惠额（结算 2 位）
     * @param settleRate           有效折扣率
     * @param sourceEnum           来源 code（CONFIG/CASHIER）
     */
    public void applySettleDiscountSnapshot(
        BigDecimal discountedReceivable,
        BigDecimal discountGap,
        BigDecimal settleRate,
        String sourceEnum
    ) {
        // 折后应收写入 discount_amount（与费用明细应收口径一致）
        this.discountAmount = discountedReceivable;
        // 优惠额写入 system_discount_price（DM v1.6.25 语义）
        this.systemDiscountPrice = discountGap;
        this.settleDiscountRate = settleRate;
        this.settleDiscountSourceEnum = sourceEnum;
    }
}
