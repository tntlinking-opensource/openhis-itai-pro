/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.advice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.ServiceRequestDetailSummaryDto;
import com.openhis.health.application.shared.advice.AdviceExtensionJsonParser.AdvicePackageChildItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * IF-IP-DOR-10：医生站医嘱统一列表行（与 AS-IP-DOR-003-SDD §3.2、AS BR-ORD-019 对齐）。
 *
 * <p>类型锚点：仅使用 {@code productDefinitionId} + {@code productTableName}（与 IF-IP-DOR-11 一致的三张<strong>定义表</strong>物理名），
 * 不下发 {@code MEDICATION}/{@code SERVICE} 等间接资源类型码。</p>
 *
 * <p>部分字段在底层实体尚未投影皮试结果、拆零比等时允许为 {@code null}，由装配层按域模型演进逐步补全。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站医嘱统一列表行")
public class InpatientDoctorStationAdviceRowDto {

    /**
     * 稳定行键：{@code requestId} + U+0001(SOH) + {@code productTableName}（A.9.1）。
     * 前端表格 rowKey、勾选与签发目标定位均依赖此键，禁止仅用 requestId（药/诊表不同会碰撞）。
     */
    @Schema(description = "稳定行键：requestId + SOH(U+0001) + productTableName")
    private String listRowKey;

    /** 医嘱请求主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "医嘱请求主键")
    private Long requestId;

    /** 住院就诊主键（与列表/暂存请求 encounterId 一致，用于门控与收费锚点）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 就诊标识。 */
    @Schema(description = "就诊标识")
    private Long encounterId;

    /** 患者主键（与医生站患者卡片一致，AppService 须与读模型快照比对）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 患者标识。 */
    @Schema(description = "患者标识")
    private Long patientId;

    /** 与 IF-IP-DOR-11 对齐的目录定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 产品定义主键（与 productTableName 成对）。 */
    @Schema(description = "产品定义主键（与 productTableName 成对）")
    private Long productDefinitionId;

    /**
     * 定义层物理表名（住院医生站仅两张：{@code med_medication_definition}、{@code wkf_activity_definition}；
     * 与 IF-IP-DOR-11 目录、签发目标 {@code productTableName} 同口径，不下发间接资源类型码）。
     */
    @Schema(description = "产品定义表名：med_medication_definition / wkf_activity_definition")
    private String productTableName;

    @Schema(description = "医嘱展示名")
    /** 规格或大小展示。 */
    private String adviceDisplayName;

    @Schema(description = "规格或大小展示")
    private String specificationText;

    @Schema(description = "请求数量")
    /** 请求数量。 */
    private BigDecimal quantity;

    /** 请求单位编码。 */
    @Schema(description = "请求单位编码")
    private String unitCode;

    /** 请求单位展示名。 */
    @Schema(description = "请求单位展示名")
    private String unitName;

    /** 计划执行次数（诊疗临嘱）。 */
    @Schema(description = "计划执行次数（诊疗临嘱）")
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

    /**
     * 拆零比（相对拆零单位倍数；底层未投影时为 null，禁止前端自行推算）。
     */
    @Schema(description = "拆零比")
    private BigDecimal partPercent;

    /** 执行或发药位置 id。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "执行或发药位置 id")
    private Long performLocationId;

    @Schema(description = "位置展示名")
    private String performLocationName;

    /**
     * 护士执行后发放单主库位 id（同医嘱多条发放时取发放主键最小的一条；未执行或无发放时为 {@code null}）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "护士执行后发药库位 id（发放主键升序首条）")
    private Long fifoDispenseLocationId;

    @Schema(description = "护士执行后发药库位名称")
    /** 诊断定义名称。 */
    private String fifoDispenseLocationName;

    @Schema(description = "诊断定义名称")
    private String conditionDefinitionName;

    /** 诊断定义主键（药品/诊疗/耗材请求均映射各自表的 {@code condition_definition_id}）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 诊断定义主键。 */
    @Schema(description = "诊断定义主键")
    private Long conditionDefinitionId;

    /** 就诊诊断主键（药品/诊疗请求均映射各自表的 {@code encounter_diagnosis_id}）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 就诊诊断主键。 */
    @Schema(description = "就诊诊断主键")
    private Long encounterDiagnosisId;

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

    /** 药品成组号（同组展示相邻；仅药品行有效）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 药品成组号。 */
    @Schema(description = "药品成组号")
    private Long groupId;

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

    /** 前置诊疗申请主键（皮试等 based_on 锚定 wkf_service_request 时回填）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 前置服务申请主键。 */
    @Schema(description = "前置服务申请主键")
    private Long basedOnServiceRequestId;

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;

    /** 嘱托内容。 */
    @Schema(description = "嘱托内容")
    private String entrustContent;

    /** 诊疗套餐子项列表。 */
    @Schema(description = "诊疗套餐子项列表")
    private List<AdvicePackageChildItemDto> packageChildren;

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

    /**
     * 行级收费汇总态 code：同一 request 下多条 {@code adm_charge_item} 归并后的最高优先级状态
     * （草稿/计划/已结等，具体枚举以收费域为准）。
     */
    @Schema(description = "行级收费汇总态 code")
    private String chargeStatusEnum;

    @Schema(description = "行级收费汇总态展示文案")
    /** 请求状态 code。 */
    private String chargeStatusEnumText;

    @Schema(description = "请求状态 code")
    private String statusEnum;

    @Schema(description = "请求状态展示文案")
    /** 请求状态展示文案。 */
    private String statusEnumText;

    /** 列表状态列展示码（退费优先于请求态）。 */
    @Schema(description = "列表状态列展示码（退费优先于请求态）")
    private String displayStatusEnum;

    /** 列表状态列展示文案（退费优先于请求态）。 */
    @Schema(description = "列表状态列展示文案（退费优先于请求态）")
    private String displayStatusEnumText;

    /** 开立医师主键（药品取 requester/recorder，诊疗取 requester）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 开立者主键。 */
    @Schema(description = "开立者主键")
    private Long authoredById;

    /** 开立者姓名。 */
    @Schema(description = "开立者姓名")
    private String authoredByName;

    /** 是否当前登录医师开立。 */
    @Schema(description = "是否当前登录医师开立")
    private Boolean isAuthoredByCurrentUser;

    /** 开立时间 ISO-8601 local。 */
    @Schema(description = "开立时间 ISO-8601 local")
    private String authoredAt;

    /** 停嘱或结束时间 ISO-8601 local。 */
    @Schema(description = "停嘱或结束时间 ISO-8601 local")
    private String endTime;

    /** 签署人主键（已签发且域投影时；未签发为 null）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 签署人主键。 */
    @Schema(description = "签署人主键")
    private Long signedById;

    /** 签署时间 ISO-8601 local。 */
    @Schema(description = "签署时间 ISO-8601 local")
    private String signedAt;

    /**
     * 长临医嘱类型 code（{@link com.openhis.health.domain.share.enums.TherapyType}：LONG_TERM / TEMPORARY）。
     * 写路径长临均仅 Request；费用与发放由护士执行落库（BR-IP-ORD-005）。
     */
    @Schema(description = "长临医嘱类型 code")
    private String therapyTypeCode;

    @Schema(description = "长临医嘱类型展示名")
    /** 医生站主表展示排序号。 */
    private String therapyTypeText;

    @Schema(description = "医生站主表展示排序号")
    private Integer sortNumber;

    @Schema(description = "窄列摘要（不得作为唯一医嘱内容来源）")
    /** 窄列摘要（不得作为唯一医嘱内容来源）。 */
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

    /**
     * 诊疗服务申请类目 code（如出院 {@code 34}、转科 {@code 32}；常规诊疗行通常为空或由活动定义推导）。
     */
    @Schema(description = "诊疗服务类目 code（出院/转科等特殊医嘱）")
    private String serviceCategoryEnum;

    @Schema(description = "出院方式 code")
    /** 出院方式展示名。 */
    private String outWayCode;

    @Schema(description = "出院方式展示名")
    private String outWayName;

    @Schema(description = "出院原因")
    /** 出院原因。 */
    private String reasonText;

    /** 计划出院时间 ISO-8601 local。 */
    @Schema(description = "计划出院时间 ISO-8601 local")
    private String plannedDischargeTime;

    /** 服务申请明细摘要（申请单行）。 */
    @Schema(description = "服务申请明细摘要（申请单行）")
    private List<ServiceRequestDetailSummaryDto> serviceRequestDetails;
}
