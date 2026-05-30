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

package com.openhis.health.application.inpatient.doctorstation.advice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.querysupport.advice.dto.AdvicePackageChildItemDto;
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

    /** 药品申请或诊疗服务申请主键（与 productTableName 成对标识资源类型）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "医嘱请求主键")
    private Long requestId;

    /** 住院就诊主键（与列表/暂存请求 encounterId 一致，用于门控与收费锚点）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识")
    private Long encounterId;

    /** 患者主键（与医生站患者卡片一致，AppService 须与读模型快照比对）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者标识")
    private Long patientId;

    /** 与 IF-IP-DOR-11 对齐的目录定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "产品定义主键（与 productTableName 成对）")
    private Long productDefinitionId;

    /**
     * 定义层物理表名（住院医生站仅两张：{@code med_medication_definition}、{@code wkf_activity_definition}；
     * 与 IF-IP-DOR-11 目录、签发目标 {@code productTableName} 同口径，不下发间接资源类型码）。
     */
    @Schema(description = "产品定义表名：med_medication_definition / wkf_activity_definition")
    private String productTableName;

    /** 列表主展示名：药品商品名或诊疗活动名称（由 OrderInfo + 产品读模型装配）。 */
    @Schema(description = "医嘱展示名")
    private String adviceDisplayName;

    /** 规格或包装大小展示（药品 total_volume/comprised_text，诊疗活动无规格时可为空）。 */
    @Schema(description = "规格或大小展示")
    private String specificationText;

    /** 医嘱开立数量（与请求表 quantity 一致，列表计价乘数来源）。 */
    @Schema(description = "请求数量")
    private BigDecimal quantity;

    /** 开立单位术语 code（与目录/请求 unit_code 对齐）。 */
    @Schema(description = "请求单位编码")
    private String unitCode;

    /** 开立单位中文展示（术语字典批量解析）。 */
    @Schema(description = "请求单位展示名")
    private String unitName;

    /** 计划执行次数（诊疗临嘱；护士站拆解上限；长嘱按频次可为空）。 */
    @Schema(description = "计划执行次数（诊疗临嘱）")
    private Integer executionCount;

    /** 单次剂量数值（药品行；诊疗行通常为 null）。 */
    @Schema(description = "单次剂量（药品）")
    private BigDecimal dose;

    /** 剂量单位术语 code。 */
    @Schema(description = "剂量单位编码")
    private String doseUnitCode;

    /** 剂量单位中文展示。 */
    @Schema(description = "剂量单位展示名")
    private String doseUnitName;

    /** 给药途径/用法术语 code（药品）。 */
    @Schema(description = "给药途径/用法编码")
    private String methodCode;

    /** 给药途径中文展示。 */
    @Schema(description = "用法展示名")
    private String methodName;

    /** 用药频次术语 code。 */
    @Schema(description = "用药频次编码")
    private String rateCode;

    /** 用药频次中文展示。 */
    @Schema(description = "频次展示名")
    private String rateName;

    /** 产品批号（药品临时医嘱、与收费批号售价对齐时展示）。 */
    @Schema(description = "产品批号")
    private String lotNumber;

    /** 用药天数或疗程天数（药品 dispense_per_duration）。 */
    @Schema(description = "用药天数")
    private Integer dispensePerDuration;

    /**
     * 拆零比（相对拆零单位倍数；底层未投影时为 null，禁止前端自行推算）。
     */
    @Schema(description = "拆零比")
    private BigDecimal partPercent;

    /** 执行科室或发药库位主键（药品 perform_location / OrderInfo location）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "执行或发药位置 id")
    private Long performLocationId;

    /** 执行科室或发药库位名称（批量库位查询回填）。 */
    @Schema(description = "位置展示名")
    private String performLocationName;

    /**
     * 签发后 FIFO 发放首选库位 id（同医嘱多条发放时取发放主键最小的一条；未签发或无发放行为 {@code null}）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "FIFO 发放首选库位 id（发放主键升序首条）")
    private Long fifoDispenseLocationId;

    /** FIFO 发放首选库位展示名。 */
    @Schema(description = "FIFO 发放首选库位名称")
    private String fifoDispenseLocationName;

    /** 诊断定义名称（由 {@link #conditionDefinitionId} 批量解析）。 */
    @Schema(description = "诊断定义名称")
    private String conditionDefinitionName;

    /** 诊断定义主键（药品/诊疗/耗材请求均映射各自表的 {@code condition_definition_id}）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "诊断定义主键")
    private Long conditionDefinitionId;

    /** 是否需皮试（药品 skin_test_flag；未投影时为 null）。 */
    @Schema(description = "是否皮试")
    private Boolean skinTestFlag;

    /** 皮试标志中文「是/否」展示。 */
    @Schema(description = "皮试标志展示文案")
    private String skinTestFlagText;

    /** 是否注射/输液医嘱（药品 infusion_flag）。 */
    @Schema(description = "是否注射或输液相关")
    private Boolean injectionFlag;

    /** 注射标志中文展示。 */
    @Schema(description = "注射标志展示文案")
    private String injectionFlagText;

    /** 皮试结果术语 code（执行后回填；未做皮试时为 null）。 */
    @Schema(description = "皮试结果编码")
    private String skinTestOutcomeCode;

    /** 皮试结果中文展示。 */
    @Schema(description = "皮试结果展示文案")
    private String skinTestOutcomeText;

    /** 药品处方号（签发后按日序列生成，同一处方号可对应多行药品）。 */
    @Schema(description = "处方号")
    private String prescriptionNo;

    /** 药品成组号（同组展示相邻；仅药品行有效）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "药品成组号")
    private Long groupId;

    /** 中药付数（TCM 标志为真时由 chinese_herbs_dose_quantity 取整）。 */
    @Schema(description = "中药付数")
    private Integer chineseHerbsDoseQuantity;

    /** 是否代煎（中药 suffering_flag）。 */
    @Schema(description = "是否代煎")
    private Boolean decoctionDelegatedFlag;

    /** 用药说明术语 code（dosage_instruction）。 */
    @Schema(description = "用药说明编码")
    private String dosageInstructionCode;

    /** 用药说明中文展示。 */
    @Schema(description = "用药说明展示名")
    private String dosageInstructionText;

    /** 滴速术语 code（输液医嘱）。 */
    @Schema(description = "滴速编码")
    private String speedCode;

    /** 滴速中文展示。 */
    @Schema(description = "滴速展示名")
    private String speedName;

    /** 前置诊疗申请主键（皮试等 based_on 锚定 wkf_service_request 时回填）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "前置服务申请主键")
    private Long basedOnServiceRequestId;

    /** 医嘱备注（请求表 remark）。 */
    @Schema(description = "备注")
    private String remark;

    /** 诊疗嘱托正文（service_request.entrust_content）。 */
    @Schema(description = "嘱托内容")
    private String entrustContent;

    /** 诊疗套餐子项（子表事实，禁止对外透出 children_json 串）。 */
    @Schema(description = "诊疗套餐子项列表")
    private List<AdvicePackageChildItemDto> packageChildren;

    /** 主展示收费明细 id。 */
    @JsonSerialize(using = ToStringSerializer.class)
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

    /**
     * 行级收费汇总态 code：同一 request 下多条 {@code adm_charge_item} 归并后的最高优先级状态
     * （草稿/计划/已结等，具体枚举以收费域为准）。
     */
    @Schema(description = "行级收费汇总态 code")
    private String chargeStatusEnum;

    /** 行级收费汇总态中文展示，与 {@link #chargeStatusEnum} 成对下发。 */
    @Schema(description = "行级收费汇总态展示文案")
    private String chargeStatusEnumText;

    /** 医嘱请求生命周期状态 code（草稿/已签发/已停嘱等，药/诊各自域枚举投影）。 */
    @Schema(description = "请求状态 code")
    private String statusEnum;

    /** 医嘱请求状态中文展示，与 {@link #statusEnum} 成对下发。 */
    @Schema(description = "请求状态展示文案")
    private String statusEnumText;

    /** 列表「状态」列展示码（退费优先于请求态）。 */
    @Schema(description = "列表状态列展示码（退费优先于请求态）")
    private String displayStatusEnum;

    /** 列表「状态」列展示文案（退费为待退费/已退费）。 */
    @Schema(description = "列表状态列展示文案（退费优先于请求态）")
    private String displayStatusEnumText;

    /** 开立医师主键（药品取 requester/recorder，诊疗取 requester）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "开立者主键")
    private Long authoredById;

    /** 开立医师姓名（practitioner 批量解析）。 */
    @Schema(description = "开立者姓名")
    private String authoredByName;

    /** 是否当前登录用户开立（用于「我的医嘱」筛选扩展）。 */
    @Schema(description = "是否当前登录医师开立")
    private Boolean isAuthoredByCurrentUser;

    /** 开立时间（ISO-8601 local，无偏移；来自 req_authored_time）。 */
    @Schema(description = "开立时间 ISO-8601 local")
    private String authoredAt;

    /** 停嘱或疗程结束时间（长期药品 effective_dose_end / 诊疗 occurrence_end_time）。 */
    @Schema(description = "停嘱或结束时间 ISO-8601 local")
    private String endTime;

    /** 签署人主键（已签发且域投影时；未签发为 null）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "签署人主键")
    private Long signedById;

    /** 签署时间 ISO-8601 local（已签发时）。 */
    @Schema(description = "签署时间 ISO-8601 local")
    private String signedAt;

    /**
     * 长临医嘱类型 code（{@link com.openhis.health.domain.share.enums.TherapyType}：LONG_TERM / TEMPORARY）。
     * 驱动住院分轨：长期仅 Request；临时药品与门诊一致（暂存费用、签发 FIFO 建发放）（BR-IP-ORD-005）。
     */
    @Schema(description = "长临医嘱类型 code")
    private String therapyTypeCode;

    /** 长临医嘱类型中文展示（如「长期」「临时」），由装配层按 code 解析。 */
    @Schema(description = "长临医嘱类型展示名")
    private String therapyTypeText;

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

    /**
     * 诊疗服务申请类目 code（如出院 {@code 34}、转科 {@code 32}；常规诊疗行通常为空或由活动定义推导）。
     */
    @Schema(description = "诊疗服务类目 code（出院/转科等特殊医嘱）")
    private String serviceCategoryEnum;

    /** 出院方式字典 code（仅出院类 ServiceRequest + EncounterHistory 里程碑）。 */
    @Schema(description = "出院方式 code")
    private String outWayCode;

    /** 出院方式中文展示（术语 out_way_code 解析）。 */
    @Schema(description = "出院方式展示名")
    private String outWayName;

    /** 出院原因/备注（持久化 reason_text）。 */
    @Schema(description = "出院原因")
    private String reasonText;

    /** 计划出院时间 ISO-8601 local（持久化 occurrence_start_time）。 */
    @Schema(description = "计划出院时间 ISO-8601 local")
    private String plannedDischargeTime;
}
