/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.orderaudit.dto;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 医嘱校对列表行（IF-IP-AUD-01，BR-IP-AUD-006）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医嘱校对列表行")
public class NurseStationOrderAuditRowDto {

    /** 稳定行键：requestId + SOH(U+0001) + orderType。 */
    @Schema(description = "稳定行键：requestId + SOH(U+0001) + orderType")
    private String listRowKey;

    /** 药品/诊疗申请主键。 */
    @Schema(description = "药品/诊疗申请主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long requestId;

    /** 医嘱类型：MED_REQUEST / SERVICE_REQUEST。 */
    @Schema(description = "医嘱类型：MED_REQUEST / SERVICE_REQUEST")
    private String orderType;

    /** 就诊主键。 */
    @Schema(description = "就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 患者主键。 */
    @Schema(description = "患者主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 请求状态 code。 */
    @Schema(description = "请求状态 code")
    private String statusEnum;

    /** 请求状态中文展示。 */
    @Schema(description = "请求状态中文展示")
    private String statusDisplay;

    /** 长临 code。 */
    @Schema(description = "长临 code")
    private String therapyEnum;

    /** 长临展示。 */
    @Schema(description = "长临展示")
    private String therapyDisplay;

    /** 医嘱名称摘要。 */
    @Schema(description = "医嘱名称摘要")
    private String adviceDisplayName;

    /**
     * 医嘱备注（药嘱/诊嘱 {@code remark}；护士站随医嘱内容展示）。
     */
    @Schema(description = "医嘱备注")
    private String remark;

    /** 规格/大小；诊疗可为 null。 */
    @Schema(description = "规格/大小；诊疗可为 null")
    private String specificationText;

    /**
     * 拆零比（目录 {@code package_unit_ratio}：1 包装 = N 拆零；仅药嘱；未维护时为 null）。
     */
    @Schema(description = "拆零比（1包装=N拆零）")
    private BigDecimal partPercent;

    /**
     * 包装装量后缀（如 {@code 1片x100}；仅药嘱；缺数据时为 null）。
     */
    @Schema(description = "包装装量后缀（如 1片x100）")
    private String packageSplitRatioText;

    /** 剂量/数量展示。 */
    @Schema(description = "剂量/数量展示")
    private String dosageDisplay;

    /**
     * 日剂量友好展示（如 {@code 每日三片}；长期药且频次可解析每日次数时有值）。
     */
    @Schema(description = "日剂量展示（如 每日三片）")
    private String dailyDoseDisplay;

    /**
     * 首日/首次用量（仅长期药品；空=首次槽用常规单次量，BR-IP-ORD-030 / BR-IP-AUD-006）。
     * 库内为最小/拆零单位数量。
     */
    @Schema(description = "首日/首次用量（仅长期药品）")
    private BigDecimal firstDose;

    /** 首日用量单位编码（与 {@link #firstDose} 成对；最小/拆零单位码）。 */
    @Schema(description = "首日用量单位编码")
    private String firstDoseUnitCode;

    /** 首日用量单位展示名。 */
    @Schema(description = "首日用量单位展示名")
    private String firstDoseUnitName;

    /**
     * 分次剂量序列（计量、逗号分隔；可空；有值时剂量列可拼序列；BR-IP-EXE-040）。
     */
    @Schema(description = "分次剂量序列（计量）")
    private String doseSchedule;

    /**
     * 分次用量序列（拆零、逗号分隔；与 doseSchedule 同序）。
     */
    @Schema(description = "分次用量序列（拆零）")
    private String quantitySchedule;

    /** 用法展示（药品）。 */
    @Schema(description = "用法展示（药品）")
    private String usageDisplay;

    /** 频次展示。 */
    @Schema(description = "频次展示")
    private String frequencyDisplay;

    /**
     * 输液滴速编码（药嘱 {@code med_medication_request.speed_code}；诊嘱为 null）。
     */
    @Schema(description = "输液滴速编码（仅药嘱）")
    private String speedCode;

    /**
     * 输液滴速展示名（术语 {@code speed_code}；无码为空）。
     */
    @Schema(description = "输液滴速展示（仅药嘱）")
    private String speedDisplay;

    /** 开立时间（{@code req_opened_time}）。 */
    @Schema(description = "开立时间（req_opened_time）")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime reqOpenedTime;

    /** 签发时间（{@code req_authored_time}；仅追溯，列表按开立倒序）。 */
    @Schema(description = "签发时间（req_authored_time）")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime reqAuthoredTime;

    /** 停嘱时间（{@code stop_time}）；未停为 null。 */
    @Schema(description = "停嘱时间（stop_time）；未停为 null")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime stopTime;

    /** 开立医生姓名。 */
    @Schema(description = "开立医生姓名")
    private String requesterName;

    /** 停嘱人主键（{@code stopper_id}）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "停嘱人主键")
    private Long stopperId;

    /** 停嘱人姓名。 */
    @Schema(description = "停嘱人姓名")
    private String stopperName;

    /** 校对人姓名（已校对及后续 Tab 展示）。 */
    @Schema(description = "校对人姓名（已校对及后续 Tab 展示）")
    private String performerCheckName;

    /** 停嘱校对人主键（{@code stop_performer_check_id}；未停嘱校对为 null）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "停嘱校对人主键")
    private Long stopPerformerCheckId;

    /** 停嘱校对时间（{@code stop_check_time}）。 */
    @Schema(description = "停嘱校对时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime stopCheckTime;

    /** 停嘱校对人姓名。 */
    @Schema(description = "停嘱校对人姓名")
    private String stopCheckerName;

    /**
     * 是否停嘱待校：STOPPED 且尚未停嘱校对且长期；前端据此高亮/筛选停嘱校对行。
     */
    @Schema(description = "是否停嘱待校")
    private Boolean stopAuditPending;

    /** 执行/发药位置展示。 */
    @Schema(description = "执行/发药位置展示")
    private String performerLocationDisplay;

    /** 诊疗类目；药品行 null。 */
    @Schema(description = "诊疗类目；药品行 null")
    private String categoryEnum;

    /** 类型列展示：药品/诊疗/出院/转科/护理/嘱托（IF-IP-AUD-01，与 medicineCategoryTab 无关；嘱托见 BR-IP-ORD-028）。 */
    @Schema(description = "类型列展示：药品/诊疗/出院/转科/护理/嘱托")
    private String orderKindDisplay;

    /**
     * 药品成组号（{@code med_medication_request.group_id}；同组前端相邻展示；仅药嘱行有效，诊嘱为 null）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "药品成组号（仅药嘱）")
    /** 药品成组号（仅药嘱）。 */
    private Long groupId;

    /** 药品组号/排序号（排序用）。 */
    @Schema(description = "药品组号/排序号（排序用）")
    private Integer groupNo;

    /** 排序号（药品）。 */
    @Schema(description = "排序号（药品）")
    private Integer sortNumber;

    /** 是否中药饮片方。 */
    @Schema(description = "是否中药饮片方")
    private Boolean tcmFlag;

    /** 处方号。 */
    @Schema(description = "处方号")
    private String prescriptionNo;

    /** 方内药品申请 id 列表。 */
    @Schema(description = "方内药品申请 id 列表")
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private java.util.List<Long> lineRequestIds;

    /** 代煎标志。 */
    @Schema(description = "代煎标志")
    private Boolean sufferingFlag;

    /** 中药付数。 */
    @Schema(description = "中药付数")
    private java.math.BigDecimal chineseHerbsDoseQuantity;

    /**
     * 是否皮试药（仅药嘱；{@code med_medication_request.skin_test_flag}）。
     * <p>true 时前端校对列表提示护士需皮试登记；诊疗行恒为 null。</p>
     */
    @Schema(description = "是否皮试药（仅药嘱）")
    private Boolean skinTestFlag;

    /**
     * companion「皮试检查」锚定的皮试药主键；药品行与无关诊疗为 null。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "皮试检查关联的皮试药 requestId")
    private Long basedOnMedicationRequestId;

    /**
     * 皮试临床状态 code（仅皮试药且护士已登记时有值；如 {@code active}=阳性）。
     */
    @Schema(description = "皮试临床状态 code（仅皮试药）")
    private String skinTestOutcomeCode;

    /**
     * 皮试临床状态展示文案（如 阳性 / 阴性）；列表跟在药名后展示。
     */
    @Schema(description = "皮试临床状态展示文案（仅皮试药）")
    private String skinTestOutcomeText;

    /**
     * 计划出院时间（出院医嘱 {@code occurrence_start_time}；医生填写；非清床/结算时刻）。
     * <p>仅出院类诊疗行（{@code category_enum=34}）有值；前端医嘱内容扩展行展示。</p>
     */
    @Schema(description = "计划出院时间（出院医嘱 occurrence_start_time）")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime plannedDischargeTime;

    /**
     * 出院/转科原因（{@code wkf_service_request.reason_text}）；普通诊疗行 null。
     */
    @Schema(description = "出院/转科原因")
    private String reasonText;
}
