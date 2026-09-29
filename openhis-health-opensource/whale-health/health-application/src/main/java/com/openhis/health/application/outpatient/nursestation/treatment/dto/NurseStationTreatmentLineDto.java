/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.nursestation.treatment.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.ServiceRequestDetailSummaryDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 门诊护士站处置单行 DTO（三列表字段名一致，IF-NUR-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊护士站处置单行")
public class NurseStationTreatmentLineDto {

    /**
     * 就诊主键。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键")
    /** 就诊主键。 */
    private Long encounterId;

    /**
     * 请求主键。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "请求主键")
    /** 请求主键。 */
    private Long requestId;

    /**
     * 发放锚点；诊疗可空。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "发放锚点；诊疗可空")
    /** 发放锚点；诊疗可空。 */
    private Long dispenseId;

    /**
     * 耗材签发批号（主发放行 {@code wkf_device_dispense.lot_number}；药品/诊疗行可空）。
     */
    @Schema(description = "耗材签发批号")
    private String lotNumber;

    /**
     * 请求表语义短码（SERVICE/MEDICATION/DEVICE）。
     */
    @Schema(description = "请求表语义短码")
    private String requestTable;

    /**
     * 与 requestTable 同义展示（可选）。
     */
    @Schema(description = "与 requestTable 同义展示（可选）")
    private String orderType;

    /**
     * 目录编码。
     */
    @Schema(description = "目录编码")
    private String itemCode;

    /**
     * 处方/申请单号：药品行为 {@code med_medication_request.prescription_no}（或回落 busNo）；
     * 诊疗行为 {@code wkf_service_request.prescription_no}，{@code AR} 前缀表示检验/检查/治疗申请单整单号。
     */
    @Schema(description = "处方/申请单号（诊疗 AR 前缀为申请单号）")
    /** 处方/申请单号（诊疗 AR 前缀为申请单号）。 */
    private String prescriptionNo;

    /**
     * 名称。
     */
    @Schema(description = "名称")
    private String itemName;

    /**
     * 规格。
     */
    @Schema(description = "规格")
    private String specText;

    /**
     * 拼音检索码。
     */
    @Schema(description = "拼音检索码")
    private String pyStr;

    /**
     * 五笔检索码。
     */
    @Schema(description = "五笔检索码")
    private String wbStr;

    /**
     * 诊疗类目码（仅诊疗列表有意义）。
     */
    @Schema(description = "诊疗类目码（仅诊疗列表有意义）")
    private String serviceCategoryCode;

    /**
     * 诊疗类目展示文案。
     */
    @Schema(description = "诊疗类目展示文案")
    private String serviceCategoryCodeText;

    /**
     * 耗材类目码（仅耗材列表有意义）。
     */
    @Schema(description = "耗材类目码（仅耗材列表有意义）")
    private String deviceCategoryCode;

    /**
     * 耗材类目展示文案。
     */
    @Schema(description = "耗材类目展示文案")
    private String deviceCategoryCodeText;

    /**
     * 药品类目码（仅药品列表有意义）。
     */
    @Schema(description = "药品类目码（仅药品列表有意义）")
    private String medCategoryCode;

    /**
     * 药品类目展示文案。
     */
    @Schema(description = "药品类目展示文案")
    private String medCategoryCodeText;

    /**
     * 数量。
     */
    @Schema(description = "数量")
    private BigDecimal quantity;

    /**
     * 单位编码。
     */
    @Schema(description = "单位编码")
    private String unitCode;

    /**
     * 单位展示名（术语 {@code unit_code} 映射优先；药品另可回落产品字典文案，对齐 **BR-ORD-022**；诊疗/耗材亦回填供处置单打印）。
     */
    @Schema(description = "单位展示名")
    private String unitName;

    /**
     * 单次剂量（**仅药品**；持久化 {@code med_medication_request.dose}）。
     */
    @Schema(description = "单次剂量")
    private BigDecimal dose;

    /**
     * 剂量单位编码（**仅药品**；持久化 {@code med_medication_request.dose_unit_code}）。
     */
    @Schema(description = "剂量单位编码")
    private String doseUnitCode;

    /**
     * 剂量单位展示名（**仅药品**；{@link com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler#resolveDoseUnitDisplayName}，对齐 **BR-ORD-022**）。
     */
    @Schema(description = "剂量单位展示名")
    private String doseUnitName;

    /**
     * 单次剂量展示串（**仅药品**）：最小单位为主，必要时括号附计量；单位相同时无括号。
     */
    @Schema(description = "单次剂量展示串（最小单位为主，括号附计量；单位相同时无括号）")
    private String dosageDisplay;

    /** 单次剂量对应的最小单位数量（**仅药品**）；单位相同或无法换算时为 null。 */
    @Schema(description = "单次剂量对应的最小单位数量")
    private BigDecimal splitDose;

    /** 最小单位编码（**仅药品**）。 */
    @Schema(description = "最小单位编码")
    private String splitDoseUnitCode;

    /** 最小单位展示名（**仅药品**）。 */
    @Schema(description = "最小单位展示名")
    private String splitDoseUnitName;

    /**
     * 给药途径/用法编码（**仅药品**；持久化 {@code med_medication_request.method_code}）。
     */
    @Schema(description = "给药途径/用法编码")
    private String methodCode;

    /**
     * 给药途径/用法展示名（**仅药品**；术语 {@code method_code} 映射）。
     */
    @Schema(description = "给药途径/用法展示名")
    private String methodName;

    /**
     * 用药频次编码（**仅药品**；持久化 {@code med_medication_request.rate_code}）。
     */
    @Schema(description = "用药频次编码")
    private String rateCode;

    /**
     * 用药频次展示名（**仅药品**；频次码表映射）。
     */
    @Schema(description = "用药频次展示名")
    private String rateName;

    /**
     * 用药天数（**仅药品**；持久化 {@code med_medication_request.dispense_per_duration}，与 IF-DOR-10 列表列回显一致）。
     */
    @Schema(description = "用药天数")
    private Integer dispensePerDuration;

    /**
     * 是否输液/注射相关（**仅药品**；优先 {@code med_medication_request.infusion_flag}，未落库时回落 {@code med_medication_definition.inject_flag}）。
     */
    @Schema(description = "是否输液/注射相关")
    private Boolean infusionFlag;

    /**
     * 输液/注射相关展示（是/否）。
     */
    @Schema(description = "输液/注射相关展示")
    private String infusionFlagText;

    /**
     * 是否皮试药（**仅药品**；持久化 {@code med_medication_request.skin_test_flag}）。
     */
    @Schema(description = "是否皮试药")
    private Boolean skinTestFlag;

    /**
     * 是否中药医嘱（**仅药品**；持久化 {@code med_medication_request.tcm_flag}）。
     */
    @Schema(description = "是否中药医嘱")
    private Boolean tcmFlag;

    /**
     * 仅诊疗：计划执行次数。
     */
    @Schema(description = "仅诊疗：计划执行次数")
    private Integer plannedPerformCount;

    /**
     * 仅药品：execute_num。
     */
    @Schema(description = "仅药品：execute_num")
    private Integer medicationExecuteNum;

    /**
     * 仅诊疗：完成次数。
     */
    @Schema(description = "仅诊疗：完成次数")
    private Integer performCount;

    /**
     * 仅诊疗：取消次数。
     */
    @Schema(description = "仅诊疗：取消次数")
    private Integer cancelCount;

    /**
     * 关联收费明细归并状态（同请求锚点多行按收费态优先级归并，含发药/发材映射）。
     */
    @Schema(description = "关联收费明细归并状态")
    private String chargeStatusEnum;

    /**
     * 关联收费明细归并状态展示文案。
     */
    @Schema(description = "关联收费明细归并状态展示文案")
    private String chargeStatusEnumText;

    /**
     * 列表「状态」列展示码：收费退费时为 REFUNDING/REFUNDED，否则由前端按执行进度展示。
     */
    @Schema(description = "列表状态列展示码（退费态）")
    private String displayStatusEnum;

    /**
     * 列表「状态」列展示文案：退费时为待退费/已退费。
     */
    @Schema(description = "列表状态列展示文案（退费态）")
    private String displayStatusEnumText;

    /**
     * 关联收费明细金额之和。
     */
    @Schema(description = "关联收费明细金额之和")
    private BigDecimal displayTotalPrice;

    /**
     * 单价（可选；耗材侧栏取关联账单 {@code adm_charge_item.id} 升序首条 {@code unit_price}，与收费状态首条口径一致）。
     */
    @Schema(description = "单价（可选）")
    private BigDecimal unitPrice;

    /**
     * 发放状态持久化码。
     */
    @Schema(description = "发放状态持久化码")
    private String dispenseStatusEnum;

    /**
     * 发放状态展示文案。
     */
    @Schema(description = "发放状态展示文案")
    private String dispenseStatusEnumText;

    /**
     * 退费关联请求主键。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "退费关联请求主键")
    /** 退费关联请求主键。 */
    private Long refundPairRequestId;

    /**
     * 成组主键。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "成组主键")
    /** 成组主键。 */
    private Long groupId;

    /**
     * 排序号。
     */
    @Schema(description = "排序号")
    private Integer sortNumber;

    /**
     * 最近一次已完成执行的主执行人主键（读侧由 {@code cln_procedure_performer} 装配）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "最近主执行人主键")
    /** 最近主执行人主键。 */
    private Long lastPerformerPractitionerId;

    /**
     * 最近一次已完成执行的主执行人展示名。
     */
    @Schema(description = "最近主执行人展示名")
    private String lastPerformerDisplay;

    /**
     * 申请单行纯备注（`wkf_service_request.remark`；非申请单行可空）。
     */
    @Schema(description = "申请单行纯备注")
    private String remark;

    /**
     * 申请单行部位/样本明细摘要（`AR` 前缀申请单；对齐 IF-DOR-10 `serviceRequestDetails`）。
     */
    @Schema(description = "申请单部位/样本明细摘要")
    private List<ServiceRequestDetailSummaryDto> serviceRequestDetails;

    /** 诊疗活动定义主键（检验/处置行；供合管打印分组）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "诊疗活动定义主键")
    private Long activityDefinitionId;

    /** 合管检验设备主键；未配置合管时为 null。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "合管检验设备主键")
    private Long specimenTubeMergeDeviceId;

    /**
     * 医嘱优先级 code（DM-WKF-004；加急为 {@code EM}；采血贴印「急」用）。
     */
    @Schema(description = "医嘱优先级 code（加急=EM）")
    private String priorityEnum;
}
