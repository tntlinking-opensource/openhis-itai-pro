/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.outpatient.doctorstation.advice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.module.querysupport.advice.dto.AdvicePackageChildItemDto;
import com.openhis.health.module.querysupport.advice.dto.AdviceRequestExtensionSnapshotDto;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.ServiceRequestDetailSummaryDto;
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
/** 医生站医嘱统一列表行。 */
@Schema(description = "医生站医嘱统一列表行")
public class DoctorStationAdviceRowDto {

    @Schema(description = "稳定行键：requestId + SOH(U+0001) + productTableName")
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
    /** 产品定义主键。 */
    @Schema(description = "产品定义主键（与 productTableName 成对）")
    private Long productDefinitionId;

    /** 定义层物理表名（三张之一）。 */
    @Schema(description = "产品定义表名：med_medication_definition / wkf_activity_definition / adm_device_definition")
    private String productTableName;

    /** 医嘱展示名。 */
    @Schema(description = "医嘱展示名")
    private String adviceDisplayName;

    /** 规格/大小展示。 */
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

    /** 计划执行次数（诊疗临嘱/门诊；护士站拆解上限）。 */
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

    /** 给药途径编码。 */
    @Schema(description = "给药途径/用法编码")
    private String methodCode;

    /** 给药途径展示名。 */
    @Schema(description = "用法展示名")
    private String methodName;

    /** 用药频次编码。 */
    @Schema(description = "用药频次编码")
    private String rateCode;

    /** 用药频次展示名。 */
    @Schema(description = "频次展示名")
    private String rateName;

    /** 批号。 */
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
    private String fifoDispenseLocationName;

    @Schema(description = "诊断定义名称")
    private String conditionDefinitionName;

    /** 诊断定义主键（药品/诊疗/耗材请求均映射各自表的 {@code condition_definition_id}）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 诊断定义主键。 */
    @Schema(description = "诊断定义主键")
    private Long conditionDefinitionId;

    /** 就诊诊断主键（药品/诊疗/耗材请求均映射各自表的 {@code encounter_diagnosis_id}）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 就诊诊断主键。 */
    @Schema(description = "就诊诊断主键")
    private Long encounterDiagnosisId;

    /** 是否皮试。 */
    @Schema(description = "是否皮试")
    private Boolean skinTestFlag;

    /** 皮试标志展示。 */
    @Schema(description = "皮试标志展示文案")
    private String skinTestFlagText;

    /** 是否注射/输液相关。 */
    @Schema(description = "是否注射或输液相关")
    private Boolean injectionFlag;

    /** 注射标志展示。 */
    @Schema(description = "注射标志展示文案")
    private String injectionFlagText;

    /** 皮试结果编码。 */
    @Schema(description = "皮试结果编码")
    private String skinTestOutcomeCode;

    /** 皮试结果展示。 */
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

    /** 中药付数。 */
    @Schema(description = "中药付数")
    private Integer chineseHerbsDoseQuantity;

    /** 代煎标识。 */
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

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;

    /** 嘱托内容。 */
    @Schema(description = "嘱托内容")
    private String entrustContent;

    /** 诊疗套餐子项（子表事实，禁止对外透出 children_json 串）。 */
    @Schema(description = "诊疗套餐子项列表")
    private List<AdvicePackageChildItemDto> packageChildren;

    /** 请求扩展快照（content_json 解析，禁止对外透出原始 JSON 串）。 */
    @Schema(description = "请求扩展快照")
    private AdviceRequestExtensionSnapshotDto requestExtensions;

    /** 主展示收费明细 id。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 主展示用收费明细 id。 */
    @Schema(description = "主展示用收费明细 id")
    private Long primaryChargeItemId;

    /** 关联收费明细 id 列表。 */
    @Schema(description = "同一请求关联的全部收费明细 id")
    private List<Long> chargeItemIds;

    /** 行级展示单价（adm_charge_item.unit_price，decimal 元；接口输出固定 6 位小数）。 */
    @Schema(description = "行级展示单价（元，6 位小数；主展示收费行）")
    private BigDecimal lineDisplayUnitPrice;

    /** 行级展示总价。 */
    @Schema(description = "行级展示总价（元，6 位小数；同请求 total_price 汇总）")
    private BigDecimal lineDisplayTotalPrice;

    /**
     * 由本行请求数量 × {@link #lineDisplayUnitPrice} 推算的金额（与收费存证一致时可与 lineDisplayTotalPrice 对拍；
     * 多收费行或拆分时仅作参考）。
     */
    @Schema(description = "数量×单价推算金额（元，6 位小数）")
    private BigDecimal lineAmountByUnitAndQuantity;

    @Schema(description = "行级收费汇总态 code")
    private String chargeStatusEnum;

    @Schema(description = "行级收费汇总态展示文案")
    private String chargeStatusEnumText;

    @Schema(description = "请求状态 code")
    private String statusEnum;

    /** 请求状态展示。 */
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

    /** 开立者 id。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "开立者主键")
    private Long authoredById;

    @Schema(description = "开立者姓名")
    private String authoredByName;

    /** 是否当前用户开立。 */
    @Schema(description = "是否当前登录医师开立")
    private Boolean isAuthoredByCurrentUser;

    /** 开立时间 ISO local。 */
    @Schema(description = "开立时间 ISO-8601 local")
    private String authoredAt;

    /** 停嘱/结束时间。 */
    @Schema(description = "停嘱或结束时间 ISO-8601 local")
    private String endTime;

    /** 签署人（若已签发且可解析）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 签署人主键。 */
    @Schema(description = "签署人主键")
    private Long signedById;

    /** 签署时间。 */
    @Schema(description = "签署时间 ISO-8601 local")
    private String signedAt;

    /** 医生站主表排序号。 */
    @Schema(description = "医生站主表展示排序号")
    private Integer sortNumber;

    /** 窄列摘要。 */
    @Schema(description = "窄列摘要（不得作为唯一医嘱内容来源）")
    private String contentSummary;

    /** 产品目录分类编码（药/诊疗/耗材定义上的 category_code）。 */
    @Schema(description = "目录分类编码（定义主数据 category_code）")
    private String catalogCategoryCode;

    /** 产品目录分类展示名。 */
    @Schema(description = "目录分类展示名")
    private String catalogCategoryName;

    /** 诊疗活动类型编码（仅诊疗行：wkf_activity_definition.type_enum）。 */
    @Schema(description = "诊疗活动类型编码（检验/检查/…，仅 productTableName=wkf_activity_definition 时有值）")
    private String activityTypeCode;

    /** 诊疗活动类型展示名。 */
    @Schema(description = "诊疗活动类型展示名")
    private String activityTypeName;

    /** 申请单行部位/样本明细摘要（prescriptionNo 为 AR 前缀时有值）。 */
    @Schema(description = "服务申请明细摘要（申请单行）")
    private List<ServiceRequestDetailSummaryDto> serviceRequestDetails;
}
