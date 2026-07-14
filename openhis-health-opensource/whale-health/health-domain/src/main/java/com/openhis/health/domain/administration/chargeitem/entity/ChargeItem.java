/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.chargeitem.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
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

    /** 处方号展示/追溯（DM prescription_no）。 */
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

    /** 执行人主键（DM performer_id）。 */
    @Column(name = "performer_id")
    private Long performerId;

    /** 执行机构主键（DM performing_org_id）。 */
    @Column(name = "performing_org_id")
    private Long performingOrgId;

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

    /** 改价原因码（DM override_reason_code）。 */
    @Column(name = "override_reason_code")
    private String overrideReasonCode;

    /** 改价原因说明（DM override_reason_text）。 */
    @Column(name = "override_reason_text")
    private String overrideReasonText;

    /** 录入人主键（DM enterer_id）。 */
    @Column(name = "enterer_id")
    private Long entererId;

    /** 录入时间（DM entered_date）。 */
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

    /** 退费关联主键（DM refund_id）。 */
    @Column(name = "refund_id")
    private Long refundId;

    /** 病情/诊断条件关联（DM condition_id）。 */
    @Column(name = "condition_id")
    private Long conditionId;

    /** 就诊诊断关联主键（DM encounter_diagnosis_id）。 */
    @Column(name = "encounter_diagnosis_id")
    private Long encounterDiagnosisId;

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
}
