/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.charge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 就诊待收费明细行 DTO。
 */
@Data
@Accessors(chain = true)
@Schema(description = "就诊待收费明细行")
public class PendingChargeItemRowDto {

    /** 收费明细 ID。 */
    @Schema(description = "收费明细 ID")
    private Long chargeItemId;

    /** 来源业务表。 */
    @Schema(description = "来源业务表")
    private String serviceTable;

    /** 来源业务主键。 */
    @Schema(description = "来源业务 ID")
    private Long serviceId;

    /** 收费项展示名称。 */
    @Schema(description = "收费项展示名称（优先三大目录读模型 product_name，锚点：adm_charge_item.product 列；缺省时回落 adm_charge_item_definition 实例列；再其次 charge_name）")
    private String itemName;

    /** 收费明细业务流水号。 */
    @Schema(description = "收费明细业务流水号（bus_no）")
    private String busNo;

    /** 单价。 */
    @Schema(description = "单价")
    private BigDecimal unitPrice;

    /** 数量值。 */
    @Schema(description = "数量")
    private BigDecimal quantityValue;

    /** 数量单位 code。 */
    @Schema(description = "数量单位")
    private String quantityUnit;

    /** 数量展示文本（不含单位）。 */
    @Schema(description = "数量展示（仅数值；单位见 quantityUnit）")
    private String quantityDisplay;

    /** 费用分类编码。 */
    @Schema(description = "费用分类编码（优先目录读模型 category_code，目录锚点同 itemName；其次 adm_charge_item_definition.type_code）")
    private String chargeCategoryCode;

    /** 费用分类展示名。 */
    @Schema(description = "费用分类展示名（优先目录读模型 category_code_dict_text，否则与 ChargeTypeCategory 对齐时解析）")
    private String chargeCategoryDisplay;

    /** 收费状态编码。 */
    @Schema(description = "收费状态编码（ChargeItemStatus.code）")
    private String statusCode;

    /** 收费状态文案。 */
    @Schema(description = "收费状态文案")
    private String statusText;

    /** 行总价。 */
    @Schema(description = "总价")
    private BigDecimal totalPrice;

    /** 收费人医务人员主键（adm_charge_item.enterer_id，BR-OPC-016）。 */
    @Schema(description = "收费人医务人员主键（仅 adm_charge_item.enterer_id，不做 updated_by 回落）")
    private Long cashierUserId;

    /** 收费人姓名。 */
    @Schema(description = "收费人姓名（能关联到医务人员主档时解析，否则为空）")
    private String cashierName;

    /** 折扣率展示文本。 */
    @Schema(description = "折扣率展示")
    private String discountRateDisplay;

    /** 记账账户主键。 */
    @Schema(description = "记账账户 ID")
    private Long accountId;

    /** 费用性质名称（账户 contract_no → 费用性质合同名称）。 */
    @Schema(description = "费用性质名称（账户 contract_no → 费用性质合同名称）")
    private String contractName;

    /** 费用性质合同主键。 */
    @Schema(description = "费用性质合同主键（账户 contract_no → fin_contract.id）")
    private Long contractId;

    /**
     * 费用性质合同分类码（账户 contract_no → fin_contract.category_enum）。
     * <p>供收银判断是否需读卡；勿与 {@link #chargeCategoryCode}（药品/项目目录分类）混淆。</p>
     */
    @Schema(description = "费用性质合同分类码（fin_contract.category_enum，如 3=省医保）")
    private String contractCategoryCode;

    /** 费用性质展示（供前端列 feeNatureDisplay）。 */
    @Schema(description = "费用性质展示（与 contractName 同源，供 IF-OPC-03 前端列 feeNatureDisplay）")
    private String feeNatureDisplay;

    /**
     * 就诊类型 code（医保医疗类别 {@code YbMedType}）。
     * <p>取值自 {@code adm_encounter_diagnosis.med_type}，按收费行 {@code encounter_diagnosis_ids} ids 串首位 id 解析。</p>
     */
    @Schema(description = "就诊类型 code（YbMedType；adm_encounter_diagnosis.med_type，按收费行 encounter_diagnosis_ids ids 串首位解析）")
    private String medTypeCode;

    /**
     * 就诊类型展示文案（{@code YbMedType#getDisplay()}）。
     * <p>供 IF-OPC-03 前端收费项目列表「就诊类型」列；未知码时为空，前端可回落 {@link #medTypeCode}。</p>
     */
    @Schema(description = "就诊类型展示（YbMedType 文案，供前端列 medTypeDisplay）")
    private String medTypeDisplay;

    /** 绑定就诊诊断主键有序串（列 encounter_diagnosis_ids；有序，首位为主诊断）。 */
    @Schema(description = "绑定就诊诊断主键有序串（encounter_diagnosis_ids）")
    private String encounterDiagnosisIds;

    /** 医保目录编码（产品读模型 yb_no）。 */
    @Schema(description = "医保目录编码（产品读模型 yb_no）")
    private String ybNo;

    /** 医保编码展示（供前端列 medicalInsuranceCode）。 */
    @Schema(description = "医保编码展示（与 ybNo 同源，供前端列 medicalInsuranceCode）")
    private String medicalInsuranceCode;

    /** 医保收费项目等级码（术语 CHRGITM_LV，如 1/2/3）。 */
    @Schema(description = "医保收费项目等级码（CHRGITM_LV）")
    private Integer chrgitmLv;

    /** 医保类别展示（甲类/乙类/丙类等）。 */
    @Schema(description = "医保类别展示（甲类/乙类/丙类）")
    private String chrgitmLvText;
}
