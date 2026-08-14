/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.advice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.shared.advice.AdviceExtensionJsonParser.AdvicePackageChildItemDto;
import com.openhis.health.application.shared.advice.AdviceExtensionJsonParser.AdviceRequestExtensionSnapshotDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * IF-DOR-10：医生站医嘱统一列表行（与 AS-OP-DOR-003-SDD §3.2、AS BR-ORD-019 对齐）。
 *
 * <p>类型锚点：仅使用 {@code productDefinitionId} + {@code productTableName}（与 IF-DOR-11 一致的三张<strong>定义表</strong>物理名），
 * 不下发 {@code MEDICATION}/{@code SERVICE} 等间接资源类型码。</p>
 *
 * <p>部分字段在底层实体尚未投影皮试结果、拆零比等时允许为 {@code null}，由装配层按域模型演进逐步补全。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站医嘱统一列表行")
public class DoctorStationAdviceRowDto {

    @Schema(description = "稳定行键：requestId + SOH(U+0001) + productTableName")
    /** 稳定行键：requestId + SOH(U+0001) + productTableName。 */
    private String listRowKey;

    /** 药品/耗材/诊疗请求聚合主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 医嘱请求主键。 */
    @Schema(description = "医嘱请求主键")
    private Long requestId;

    /** 就诊标识。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 就诊标识。 */
    @Schema(description = "就诊标识")
    private Long encounterId;

    /** 患者标识。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 患者标识。 */
    @Schema(description = "患者标识")
    private Long patientId;

    /** 与 IF-DOR-11 对齐的目录定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 产品定义主键（与 productTableName 成对）。 */
    @Schema(description = "产品定义主键（与 productTableName 成对）")
    private Long productDefinitionId;

    /** 产品定义表名：med_medication_definition / wkf_activity_definition / adm_device_definition。 */
    @Schema(description = "产品定义表名：med_medication_definition / wkf_activity_definition / adm_device_definition")
    private String productTableName;

    /** 医嘱展示名。 */
    @Schema(description = "医嘱展示名")
    private String adviceDisplayName;

    /** 规格或大小展示。 */
    @Schema(description = "规格或大小展示")
    private String specificationText;

    /** 请求数量。 */
    @Schema(description = "请求数量")
    private BigDecimal quantity;

    /** 请求单位编码。 */
    @Schema(description = "请求单位编码")
    private String unitCode;

    /** 请求单位展示名。 */
    @Schema(description = "请求单位展示名")
    private String unitName;

    /** 计划执行次数（诊疗）。 */
    @Schema(description = "计划执行次数（诊疗）")
    private Integer executionCount;

    /** 单次剂量（药品）。 */
    @Schema(description = "单次剂量（药品）")
    private BigDecimal dose;

    /** 剂量单位编码。 */
    @Schema(description = "剂量单位编码")
    private String doseUnitCode;

    /** 剂量单位展示名。 */
    @Schema(description = "剂量单位展示名")
    private String doseUnitName;

    /**
     * 单次剂量展示串：最小单位为主，必要时括号内附计量单位（如 {@code 1支(2ml)/次}）；
     * 计量与最小单位相同时无括号（如 {@code 1片/次}）。
     */
    @Schema(description = "单次剂量展示串（最小单位为主，括号附计量；单位相同时无括号）")
    private String dosageDisplay;

    /** 单次剂量对应的最小（拆零）单位数量；单位相同或无法换算时为 null。 */
    @Schema(description = "单次剂量对应的最小单位数量")
    private BigDecimal splitDose;

    /** 最小单位编码。 */
    @Schema(description = "最小单位编码")
    private String splitDoseUnitCode;

    /** 最小单位展示名。 */
    @Schema(description = "最小单位展示名")
    private String splitDoseUnitName;

    /** 给药途径/用法编码。 */
    @Schema(description = "给药途径/用法编码")
    private String methodCode;

    /** 用法展示名。 */
    @Schema(description = "用法展示名")
    private String methodName;

    /** 用药频次编码。 */
    @Schema(description = "用药频次编码")
    private String rateCode;

    /** 频次展示名。 */
    @Schema(description = "频次展示名")
    private String rateName;

    /** 产品批号。 */
    @Schema(description = "产品批号")
    private String lotNumber;

    /** 用药天数。 */
    @Schema(description = "用药天数")
    private Integer dispensePerDuration;

    /** 拆零比。 */
    @Schema(description = "拆零比")
    private BigDecimal partPercent;

    /** 执行/发药位置 id。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 执行或发药位置 id。 */
    @Schema(description = "执行或发药位置 id")
    private Long performLocationId;

    /** 位置展示名。 */
    @Schema(description = "位置展示名")
    private String performLocationName;

    /**
     * 签发后 FIFO 发放首选库位 id（同医嘱多条发放时取发放主键最小的一条；未签发或无发放行为 {@code null}）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "FIFO 发放首选库位 id（发放主键升序首条）")
    private Long fifoDispenseLocationId;

    @Schema(description = "FIFO 发放首选库位名称")
    /** 诊断定义名称。 */
    private String fifoDispenseLocationName;

    @Schema(description = "诊断定义名称")
    private String conditionDefinitionName;

    /**
     * 绑定就诊诊断主键有序串（药品/诊疗/耗材请求均映射各自表的 {@code encounter_diagnosis_ids}；有序，首位为主诊断）。
     */
    @Schema(description = "绑定就诊诊断主键有序串")
    private String encounterDiagnosisIds;

    /** 是否皮试。 */
    @Schema(description = "是否皮试")
    private Boolean skinTestFlag;

    /** 皮试标志展示文案。 */
    @Schema(description = "皮试标志展示文案")
    private String skinTestFlagText;

    /** 是否注射或输液相关。 */
    @Schema(description = "是否注射或输液相关")
    private Boolean injectionFlag;

    /** 注射标志展示文案。 */
    @Schema(description = "注射标志展示文案")
    private String injectionFlagText;

    /** 皮试结果编码。 */
    @Schema(description = "皮试结果编码")
    private String skinTestOutcomeCode;

    /** 皮试结果展示文案。 */
    @Schema(description = "皮试结果展示文案")
    private String skinTestOutcomeText;

    /** 处方号。 */
    @Schema(description = "处方号")
    private String prescriptionNo;

    /** 药品成组号。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 药品成组号。 */
    @Schema(description = "药品成组号")
    private Long groupId;

    /**
     * 组套标识（成组药嘱与 {@code groupId} 同源；绑定耗材可对齐本字段）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "组套标识；成组药嘱与 groupId 同源")
    private Long packageId;

    /** 中药付数。 */
    @Schema(description = "中药付数")
    private Integer chineseHerbsDoseQuantity;

    /** 是否代煎。 */
    @Schema(description = "是否代煎")
    private Boolean decoctionDelegatedFlag;

    /** 用药说明编码。 */
    @Schema(description = "用药说明编码")
    private String dosageInstructionCode;

    /** 用药说明展示名。 */
    @Schema(description = "用药说明展示名")
    private String dosageInstructionText;

    /** 滴速编码。 */
    @Schema(description = "滴速编码")
    private String speedCode;

    /** 滴速展示名。 */
    @Schema(description = "滴速展示名")
    private String speedName;

    /** 前置服务申请 id（如皮试关联）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 前置服务申请主键。 */
    @Schema(description = "前置服务申请主键")
    private Long basedOnServiceRequestId;

    /** 前置药品申请主键（皮试联动等）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "前置药品申请主键（皮试联动等）")
    private Long basedOnMedicationRequestId;

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;

    /** 嘱托内容。 */
    @Schema(description = "嘱托内容")
    private String entrustContent;

    /** 诊疗套餐子项列表。 */
    @Schema(description = "诊疗套餐子项列表")
    private List<AdvicePackageChildItemDto> packageChildren;

    /** 请求扩展快照。 */
    @Schema(description = "请求扩展快照")
    private AdviceRequestExtensionSnapshotDto requestExtensions;

    /** 主展示收费明细 id。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 主展示用收费明细 id。 */
    @Schema(description = "主展示用收费明细 id")
    private Long primaryChargeItemId;

    /** 同一请求关联的全部收费明细 id。 */
    @Schema(description = "同一请求关联的全部收费明细 id")
    private List<Long> chargeItemIds;

    /** 行级展示单价（元，6 位小数；主展示收费行）。 */
    @Schema(description = "行级展示单价（元，6 位小数；主展示收费行）")
    private BigDecimal lineDisplayUnitPrice;

    /** 行级展示总价（元，6 位小数；同请求 total_price 汇总）。 */
    @Schema(description = "行级展示总价（元，6 位小数；同请求 total_price 汇总）")
    private BigDecimal lineDisplayTotalPrice;

    /**
     * 由本行请求数量 × {@link #lineDisplayUnitPrice} 推算的金额（与收费存证一致时可与 lineDisplayTotalPrice 对拍；
     * 多收费行或拆分时仅作参考）。
     */
    @Schema(description = "数量×单价推算金额（元，6 位小数）")
    private BigDecimal lineAmountByUnitAndQuantity;

    @Schema(description = "行级收费汇总态 code")
    /** 行级收费汇总态展示文案。 */
    private String chargeStatusEnum;

    @Schema(description = "行级收费汇总态展示文案")
    private String chargeStatusEnumText;

    @Schema(description = "请求状态 code")
    /** 请求状态 code。 */
    private String statusEnum;

    /** 请求状态展示文案。 */
    @Schema(description = "请求状态展示文案")
    private String statusEnumText;

    /**
     * 列表「状态」列展示码：收费退费时为 REFUNDING/REFUNDED，否则与 {@link #statusEnum} 一致。
     */
    @Schema(description = "列表状态列展示码（退费优先于请求态）")
    private String displayStatusEnum;

    /**
     * 列表「状态」列展示文案：退费为待退费/已退费，否则与 {@link #statusEnumText} 一致。
     */
    @Schema(description = "列表状态列展示文案（退费优先于请求态）")
    private String displayStatusEnumText;

    /** 开立者主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "开立者主键")
    private Long authoredById;

    @Schema(description = "开立者姓名")
    private String authoredByName;

    /** 是否当前登录医师开立。 */
    @Schema(description = "是否当前登录医师开立")
    private Boolean isAuthoredByCurrentUser;

    /** 开立时间 ISO-8601 local（{@code req_opened_time}）。 */
    @Schema(description = "开立时间 ISO-8601 local（req_opened_time）")
    private String reqOpenedTime;

    /** 停嘱时间 ISO-8601 local（{@code stop_time}）。 */
    @Schema(description = "停嘱时间 ISO-8601 local（stop_time）")
    private String stopTime;

    /** 签署人（若已签发且可解析）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 签署人主键。 */
    @Schema(description = "签署人主键")
    private Long signedById;

    /** 签发时间 ISO-8601 local（{@code req_authored_time}）。 */
    @Schema(description = "签发时间 ISO-8601 local（req_authored_time）")
    private String reqAuthoredTime;

    /** 费用性质主键（药品：{@code med_medication_request.contract_id}）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "费用性质主键")
    private Long contractId;

    /**
     * 费用性质分类展示文案（ContractCategory：自费/公费/省医保/市医保/工伤保险）。
     * <p>处方笺「费用性质」展示用；无合同时为空，前端回落「自费」。</p>
     */
    @Schema(description = "费用性质分类展示文案")
    private String feeNatureText;

    /** 医生站主表展示排序号。 */
    @Schema(description = "医生站主表展示排序号")
    private Integer sortNumber;

    /** 窄列摘要（不得作为唯一医嘱内容来源）。 */
    @Schema(description = "窄列摘要（不得作为唯一医嘱内容来源）")
    private String contentSummary;

    /** 目录分类编码（定义主数据 category_code）。 */
    @Schema(description = "目录分类编码（定义主数据 category_code）")
    private String catalogCategoryCode;

    /** 目录分类展示名。 */
    @Schema(description = "目录分类展示名")
    private String catalogCategoryName;

    /** 诊疗活动类型编码（检验/检查/…，仅 productTableName=wkf_activity_definition 时有值）。 */
    @Schema(description = "诊疗活动类型编码（检验/检查/…，仅 productTableName=wkf_activity_definition 时有值）")
    private String activityTypeCode;

    /** 诊疗活动类型展示名。 */
    @Schema(description = "诊疗活动类型展示名")
    private String activityTypeName;

    /** 服务申请明细摘要（申请单行）。 */
    @Schema(description = "服务申请明细摘要（申请单行）")
    private List<ServiceRequestDetailSummaryDto> serviceRequestDetails;
}
