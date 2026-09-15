/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.orderaudit.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 住院护士站医嘱查看「行展示内核」：药/诊共用字段（非 HTTP 契约，供各业务 Assembler 拷贝）。
 *
 * <p>时间字段与三 Request 落库列同名：{@code reqOpenedTime}/{@code reqAuthoredTime}/{@code stopTime}。</p>
 */
@Data
@Accessors(chain = true)
public class NurseStationOrderViewDisplayCore {

    /** 稳定行键：requestId + SOH + orderType。 */
    private String listRowKey;

    /** 药品/诊疗申请主键。 */
    private Long requestId;

    /** 医嘱类型：MED_REQUEST / SERVICE_REQUEST。 */
    private String orderType;

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 请求状态 code。 */
    private String statusEnum;

    /** 请求状态人读文案。 */
    private String statusDisplay;

    /** 长临 code。 */
    private String therapyEnum;

    /** 长临人读文案。 */
    private String therapyDisplay;

    /** 诊疗类目 code（仅诊嘱）。 */
    private String categoryEnum;

    /** 类型列展示：药品/诊疗/出院/转科/护理/嘱托（与西药 Tab 无关；嘱托见 BR-IP-ORD-028）。 */
    private String orderKindDisplay;

    /** 医嘱名称摘要。 */
    private String adviceDisplayName;

    /**
     * 医嘱备注（药嘱/诊嘱 remark；套餐子项覆盖 JSON 不回传展示）。
     */
    private String remark;

    /** 规格/大小；诊疗可为 null。 */
    private String specificationText;

    /**
     * 拆零比（目录 {@code package_unit_ratio}：1 包装 = N 拆零；仅药嘱；未维护时为 null）。
     */
    private BigDecimal partPercent;

    /**
     * 包装装量后缀（如 {@code 1片x100}；仅药嘱；缺数据时为 null）。
     */
    private String packageSplitRatioText;

    /**
     * 包装单位展示名（目录包装单位术语；仅药嘱；缺数据时为 null）。
     * <p>供摆药单等按「一盒一点」整包装计总量时拼接数量+单位，禁止前端写死「盒」。</p>
     */
    private String packageUnitDisplay;

    /** 剂量/数量展示（药嘱优先单次 dose；与开立总量字段语义不同）。 */
    private String dosageDisplay;

    /**
     * 开立总量展示（数量+单位；仅临时/出院带药有值）。
     *
     * <p>对齐 {@code BR-MRQ-009}：临时与出院带药 {@code quantity}/{@code unit_code} 为总量语义；
     * 长期为单次用量，本字段保持 null，摆药总量勿用本字段替代 dose×次数或一盒一点口径。</p>
     */
    private String quantityDisplay;

    /**
     * 日剂量友好展示（如 {@code 每日三片}；长期药且频次可解析每日次数时有值）。
     */
    private String dailyDoseDisplay;

    /** 用法展示（药品）。 */
    private String usageDisplay;

    /** 频次展示。 */
    private String frequencyDisplay;

    /**
     * 输液滴速编码（药嘱 {@code speed_code}；诊嘱为 null）。
     */
    private String speedCode;

    /**
     * 输液滴速展示名（术语 {@code speed_code} 解析；无码为空）。
     */
    private String speedDisplay;

    /** 开立时间（{@code req_opened_time}）。 */
    private LocalDateTime reqOpenedTime;

    /** 签发时间（{@code req_authored_time}）。 */
    private LocalDateTime reqAuthoredTime;

    /** 停嘱时间（{@code stop_time}）。 */
    private LocalDateTime stopTime;

    /** 开立人姓名。 */
    private String requesterName;

    /** 停嘱人主键（{@code stopper_id}；未停嘱为 null）。 */
    private Long stopperId;

    /** 停嘱人姓名（停嘱时间后跟人名展示）。 */
    private String stopperName;

    /** 校对人姓名（已校对/已停止/已作废 Tab 展示）。 */
    private String performerCheckName;

    /**
     * 校对科室展示名：由校对人岗位绑定病区回溯 {@code adm_location.organization_id} 解析；
     * 医嘱执行 Tab 等消费方拷贝至行 DTO。
     */
    private String performerCheckOrgDisplay;

    /** 停嘱校对人主键（{@code stop_performer_check_id}；未停嘱校对为 null）。 */
    private Long stopPerformerCheckId;

    /** 停嘱校对时间（{@code stop_check_time}）。 */
    private LocalDateTime stopCheckTime;

    /** 停嘱校对人姓名（与 {@link #performerCheckName} 同解析链）。 */
    private String stopCheckerName;

    /**
     * 是否停嘱待校：STOPPED 且 {@code stopPerformerCheckId == null} 且长期。
     */
    private Boolean stopAuditPending;

    /** 执行/发药库位展示。 */
    private String performerLocationDisplay;

    /** 成组号（药品，与医生站 group_id 同源）。 */
    private Long groupId;

    /** 成组号 int 形态（≤ Integer.MAX_VALUE 时供校对列表排序）。 */
    private Integer groupNo;

    /** 组内排序号。 */
    private Integer sortNumber;

    /**
     * 是否皮试药（仅药嘱；{@code med_medication_request.skin_test_flag}）。
     * <p>诊疗行恒为 {@code null}；供校对列表提示护士需登记皮试。</p>
     */
    private Boolean skinTestFlag;

    /**
     * companion「皮试检查」锚定的皮试药主键；药品行与无关诊疗为 null。
     */
    private Long basedOnMedicationRequestId;

    /**
     * companion「皮试检查」关联皮试药展示名；已写入 {@link #adviceDisplayName} 后缀时可空。
     */
    private String skinTestLinkedMedicationName;

    /**
     * 皮试临床状态 code（仅皮试药且已登记时有值；如 {@code active}=阳性）。
     */
    private String skinTestOutcomeCode;

    /**
     * 皮试临床状态展示文案（如 阳性 / 阴性）；与 {@link #skinTestOutcomeCode} 同源。
     */
    private String skinTestOutcomeText;
}
