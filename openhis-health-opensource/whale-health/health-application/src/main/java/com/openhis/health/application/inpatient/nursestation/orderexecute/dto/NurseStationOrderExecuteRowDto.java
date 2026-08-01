/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.orderexecute.dto;

import com.openhis.health.application.outpatient.doctorstation.advice.dto.ServiceRequestDetailSummaryDto;
import com.openhis.health.application.shared.advice.AdviceExtensionJsonParser.AdvicePackageChildItemDto;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 医嘱执行列表行（IF-IP-EXE-01，BR-IP-EXE-009/012）。
 *
 * <p>每行对应一条 VERIFIED 药/诊医嘱；四类槽位字段同构，列表按 {@code executeTab} 仅回传
 * 当前 Tab 对应类别且 {@code planTime ≤ executionDeadline} 的计划点，其余三类为空数组；
 * 医嘱展示字段与 bindItems 不因 Tab 切换而变化。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医嘱执行列表行")
public class NurseStationOrderExecuteRowDto {

    /** 稳定行键：orderId + SOH + orderType。 */
    @Schema(description = "稳定行键")
    private String listRowKey;

    /** 医嘱/申请主键。 */
    @Schema(description = "医嘱/申请主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    /** 医嘱类型：MED_REQUEST / SERVICE_REQUEST。 */
    @Schema(description = "医嘱类型")
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

    /** 请求状态展示。 */
    @Schema(description = "请求状态展示")
    private String statusEnumText;

    /** 长临 code。 */
    @Schema(description = "长临 code")
    private String therapyEnum;

    /** 长临展示。 */
    @Schema(description = "长临展示")
    private String therapyEnumText;

    /** 诊疗类目 code（护理级别 26、嘱托 52 等；药品行 null）。 */
    @Schema(description = "诊疗类目 code；药品行 null")
    private String categoryEnum;

    /**
     * 类型列展示：药品/诊疗/出院/转科/护理/嘱托（与校对 OrderView 同源；嘱托见 BR-IP-ORD-028）。
     */
    @Schema(description = "类型列展示：药品/诊疗/出院/转科/护理/嘱托")
    private String orderKindDisplay;

    /** 医嘱展示摘要。 */
    @Schema(description = "医嘱展示摘要")
    private String adviceDisplayName;

    /**
     * 医嘱备注（药嘱/诊嘱 {@code remark}；护士站随医嘱内容展示）。
     */
    @Schema(description = "医嘱备注")
    private String remark;

    /** 规格展示。 */
    @Schema(description = "规格展示")
    private String specificationText;

    /** 剂量/数量展示。 */
    @Schema(description = "剂量/数量展示")
    private String dosageDisplay;

    /** 用法展示。 */
    @Schema(description = "用法展示")
    private String usageDisplay;

    /**
     * 给药途径/用法编码（药嘱 {@code med_medication_request.method_code}；诊嘱为 null）。
     * <p>供前端按 code 筛选输液等途径，禁止依赖 {@link #usageDisplay} 文案分支。</p>
     */
    @Schema(description = "给药途径/用法编码（仅药嘱）")
    private String methodCode;

    /**
     * 是否输液/注射相关（药嘱 {@code med_medication_request.infusion_flag}；诊嘱为 null）。
     */
    @Schema(description = "是否输液/注射相关（仅药嘱）")
    private Boolean infusionFlag;

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

    /** 停嘱时间（{@code stop_time}）。 */
    @Schema(description = "停嘱时间（stop_time）")
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

    /**
     * 校对科室展示名（校对人岗位绑定病区 → {@code organization_id} 解析；**BR-IP-EXE-029**）。
     */
    @Schema(description = "校对科室展示名（执行科室）")
    private String performerCheckOrgDisplay;

    /** 药品成组号（{@code med_medication_request.group_id}；同组前端相邻展示；仅药嘱行有效，诊嘱为 null）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "药品成组号（仅药嘱）")
    private Long groupId;

    /**
     * companion「皮试检查」锚定的皮试药主键；药品行与无关诊疗为 null。
     * <p>仅用于列表与皮试药聚邻展示；执行/取消执行仍按本行 orderId 独立操作。</p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "皮试检查关联的皮试药 requestId")
    private Long basedOnMedicationRequestId;

    /** 待执行槽位。 */
    @Schema(description = "待执行槽位")
    private List<NurseStationOrderExecuteSlotDto> pendingSlots;

    /** 已执行槽位。 */
    @Schema(description = "已执行槽位")
    private List<NurseStationOrderExecuteSlotDto> executedSlots;

    /** 不执行槽位。 */
    @Schema(description = "不执行槽位")
    private List<NurseStationOrderExecuteSlotDto> skippedSlots;

    /** 已取消执行槽位。 */
    @Schema(description = "已取消执行槽位")
    private List<NurseStationOrderExecuteSlotDto> cancelledSlots;

    /** 绑定项；无模板时为 []。 */
    @Schema(description = "绑定项")
    private List<NurseStationOrderExecuteBindItemDto> bindItems;

    /**
     * 诊疗套餐子项（活动定义子表；检查/检验申请单打印「明细」列）。
     * <p>页面医嘱执行列表不展示；仅供打印等下游消费。</p>
     */
    @Schema(description = "诊疗套餐子项（打印用，列表不展示）")
    private List<AdvicePackageChildItemDto> packageChildren;

    /**
     * 服务申请明细摘要（部位/标本；检查/检验申请单打印）。
     * <p>页面医嘱执行列表不展示；仅供打印等下游消费。</p>
     */
    @Schema(description = "服务申请明细摘要（打印用，列表不展示）")
    private List<ServiceRequestDetailSummaryDto> serviceRequestDetails;

    /** 是否中药饮片方。 */
    @Schema(description = "是否中药饮片方")
    private Boolean tcmFlag;

    /** 处方号。 */
    @Schema(description = "处方号")
    private String prescriptionNo;

    /** 方内全部药品申请 id（整方执行扩行）。 */
    @Schema(description = "方内药品申请 id 列表")
    @JsonSerialize(contentUsing = ToStringSerializer.class)
    private List<Long> lineRequestIds;

    /** 代煎标志。 */
    @Schema(description = "代煎标志")
    private Boolean sufferingFlag;

    /** 中药付数。 */
    @Schema(description = "中药付数")
    private java.math.BigDecimal chineseHerbsDoseQuantity;
}
