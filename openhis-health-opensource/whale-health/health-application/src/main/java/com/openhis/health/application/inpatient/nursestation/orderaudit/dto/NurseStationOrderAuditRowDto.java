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

    /** 剂量/数量展示。 */
    @Schema(description = "剂量/数量展示")
    private String dosageDisplay;

    /** 用法展示（药品）。 */
    @Schema(description = "用法展示（药品）")
    private String usageDisplay;

    /** 频次展示。 */
    @Schema(description = "频次展示")
    private String frequencyDisplay;

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
}
