/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationrequest.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.RequestBusinessTimeKind;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.openhis.whale.core.application.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 药品申请聚合根。
 */
@Getter
@Setter
@Entity
@Table(name = "med_medication_request")
public class MedicationRequest extends BaseAuditedEntity {

    @Column(name = "bus_no", nullable = false)
    private String busNo;

    @Column(name = "prescription_no")
    private String prescriptionNo;

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "package_id")
    private Long packageId;

    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    @Column(name = "status_reason")
    private Integer statusReason;

    @Column(name = "status_changed_time")
    private LocalDateTime statusChangedTime;

    @Column(name = "intent_enum")
    private String intentEnum;

    @Column(name = "category_enum")
    private String categoryEnum;

    @Column(name = "priority_enum")
    private String priorityEnum;

    @Column(name = "perform_flag")
    private Boolean performFlag;

    @Column(name = "medication_id", nullable = false)
    private Long medicationId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    /**
     * 开嘱/申请方医务人员主键；持久化列 {@code requester_id}，与医生站「开立人 requesterId」口径一致。
     */
    @Column(name = "requester_id")
    private Long requesterId;

    @Column(name = "location_id")
    private Long locationId;

    /** 开立科室组织主键（{@code adm_organization.id}）。 */
    @Column(name = "org_id")
    private Long orgId;

    /** 发放药房库位主键（{@code adm_location.id}）；与 {@link #orgId} 分列，禁止混存。 */
    @Column(name = "perform_location")
    private Long performLocation;

    @Column(name = "perform_org")
    private Long performOrg;

    @Column(name = "performer_enum")
    private String performerEnum;

    @Column(name = "performer_id")
    private Long performerId;

    @Column(name = "device_def_id")
    private Long deviceDefId;

    @Column(name = "recorder_id")
    private Long recorderId;

    /**
     * 开立时间：首次建草稿/直建落库写一次；签发、改草稿、签退不覆盖。
     */
    @Column(name = "req_opened_time")
    private LocalDateTime reqOpenedTime;

    /**
     * 签发时间：仅签发（及无草稿直建完成态）写入；草稿暂存不写本列。
     */
    @Column(name = "req_authored_time")
    private LocalDateTime reqAuthoredTime;

    @Column(name = "report_flag")
    private Boolean reportFlag;

    @Column(name = "support_info")
    private String supportInfo;

    @Column(name = "reason_json")
    private String reasonJson;

    @Column(name = "therapy_enum", nullable = false)
    private String therapyEnum;

    @Column(name = "effective_dose_start")
    private LocalDateTime effectiveDoseStart;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    @Column(name = "execute_num")
    private Integer executeNum;

    @Column(name = "unit_code", nullable = false)
    private String unitCode;

    @Column(name = "method_code", nullable = false)
    private String methodCode;

    @Column(name = "rate_code", nullable = false)
    private String rateCode;

    @Column(name = "dose")
    private BigDecimal dose;

    @Column(name = "dose_unit_code")
    private String doseUnitCode;

    @Column(name = "max_dose")
    private BigDecimal maxDose;

    @Column(name = "first_dose")
    private BigDecimal firstDose;

    @Column(name = "first_dose_unit_code")
    private String firstDoseUnitCode;

    /**
     * 分次剂量序列（计量口径、逗号分隔；仅长期西药/中成药用量不一致；空=走 {@link #dose}；BR-MRQ-035）。
     */
    @Column(name = "dose_schedule", length = 500)
    private String doseSchedule;

    /**
     * 分次用量序列（拆零口径、逗号分隔；与 {@link #doseSchedule} 同序同段数；空=走 {@link #quantity}；BR-MRQ-035）。
     */
    @Column(name = "quantity_schedule", length = 500)
    private String quantitySchedule;

    @Column(name = "first_duration")
    private String firstDuration;

    @Column(name = "dispense_interval")
    private String dispenseInterval;

    @Column(name = "dispense_per_quantity")
    private Integer dispensePerQuantity;

    @Column(name = "dispense_per_duration")
    private Integer dispensePerDuration;

    @Column(name = "lot_number")
    private String lotNumber;

    @Column(name = "trace_no", length = 50000)
    private String traceNo;

    @Column(name = "skin_test_flag")
    private Boolean skinTestFlag;

    @Column(name = "infusion_flag")
    private Boolean infusionFlag;

    @Column(name = "speed_code")
    private String speedCode;

    @Column(name = "contract_id")
    private Long contractId;

    /**
     * 绑定就诊诊断主键有序串（有序；首位为主诊断 {@code adm_encounter_diagnosis.id}）。
     */
    @Column(name = "encounter_diagnosis_ids", length = 2000)
    private String encounterDiagnosisIds;

    @Column(name = "performer_check_id")
    private Long performerCheckId;

    @Column(name = "check_time")
    private LocalDateTime checkTime;

    /**
     * 服药计划结束时间（临床计划窗；停嘱不再写入本列）。
     */
    @Column(name = "effective_dose_end")
    private LocalDateTime effectiveDoseEnd;

    /**
     * 停嘱时间：长期停嘱写入；取消停嘱清空；执行 Gate / 频次右界读本列；改停嘱时间可覆盖（不改停嘱人）。
     */
    @Column(name = "stop_time")
    private LocalDateTime stopTime;

    /**
     * 停嘱人：停嘱操作写入；取消停嘱清空；改停嘱时间不改本列。
     */
    @Column(name = "stopper_id")
    private Long stopperId;

    /**
     * 停嘱校对人：护士确认医生停嘱后写入；取消停嘱校对或取消停嘱时清空；不改状态。
     */
    @Column(name = "stop_performer_check_id")
    private Long stopPerformerCheckId;

    /**
     * 停嘱校对时间：与 {@link #stopPerformerCheckId} 成对；改停嘱时间不改本列。
     */
    @Column(name = "stop_check_time")
    private LocalDateTime stopCheckTime;

    @Column(name = "refund_medicine_id")
    private Long refundMedicineId;

    @Column(name = "rx_type_code")
    private String rxTypeCode;

    @Column(name = "yb_class_enum")
    private String ybClassEnum;

    @Column(name = "dosage_instruction")
    private String dosageInstruction;

    @Column(name = "based_on_table")
    private String basedOnTable;

    @Column(name = "based_on_id")
    private Long basedOnId;

    @Column(name = "tcm_flag")
    private Boolean tcmFlag;

    @Column(name = "content_json")
    private String contentJson;

    @Column(name = "chinese_herbs_dose_quantity")
    private BigDecimal chineseHerbsDoseQuantity;

    @Column(name = "suffering_flag")
    private Boolean sufferingFlag;

    @Column(name = "sort_number")
    private Integer sortNumber;

    @Column(name = "generate_source_enum")
    private String generateSourceEnum;

    @Column(name = "remark")
    private String remark;

    /**
     * 初始化草稿。
     */
    public void initDraft() {
        this.statusEnum = RequestStatus.DRAFT.getCode();
        this.statusChangedTime = LocalDateTime.now();
    }

    /**
     * 初始化退药负向申请单：待退态（与耗材/诊疗负向单 {@link RequestStatus#CANCELLED} 口径一致）。
     */
    public void initAsRefundNegativeOrder() {
        this.statusEnum = RequestStatus.CANCELLED.getCode();
        this.statusChangedTime = LocalDateTime.now();
    }

    /**
     * 校验剂量语义（含分次 schedule 成对/长期/正数段；BR-MRQ-009/010/035）。
     *
     * <p>{@code quantity} 始终必填；有 {@link #quantitySchedule} 时读侧按槽取段，不读本列单值作全日每次量。</p>
     */
    public void validateDosageSemantics() {
        if (this.quantity == null || this.quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "数量必须大于 0");
        }
        if (TherapyType.LONG_TERM.getCode().equals(this.therapyEnum)
            && this.firstDose != null && this.firstDose.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT, "长期医嘱首日量必须大于 0");
        }
        // 分次用量：两列同空或同有值；有值仅长期非中药；段数一致且每段正数（频次日次数对齐由应用层 Gate）
        this.validateDoseQuantitySchedulePair();
    }

    /**
     * 校验 {@link #doseSchedule}/{@link #quantitySchedule} 成对与正数段（BR-MRQ-035）。
     *
     * <p>段数与频次日次数对齐由应用层 {@code InpatientMedicationDoseQuantityScheduleGate} 负责；
     * 本方法只守域不变量。schedule 皆空立即返回（原 dose/quantity 路径零侵入）。</p>
     *
     * @throws BusinessException 半写、中药携带、非长期携带、段数不一致或非正段
     */
    private void validateDoseQuantitySchedulePair() {
        boolean doseBlank = StringUtils.isBlank(this.doseSchedule);
        boolean quantityBlank = StringUtils.isBlank(this.quantitySchedule);
        // 一空一满：禁止半写
        if (doseBlank != quantityBlank) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT,
                "分次剂量与分次用量须同时为空或同时有值");
        }
        if (doseBlank) {
            return;
        }
        // 中药不承载分次 schedule
        if (Boolean.TRUE.equals(this.tcmFlag)) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT,
                "中药医嘱不可填写分次用量序列");
        }
        // 仅长期可有分次序列
        if (!TherapyType.LONG_TERM.getCode().equals(this.therapyEnum)) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT,
                "仅长期医嘱可填写分次用量序列");
        }
        String[] doseParts = this.doseSchedule.split(",");
        String[] quantityParts = this.quantitySchedule.split(",");
        if (doseParts.length != quantityParts.length) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT,
                "分次剂量与分次用量段数须一致");
        }
        if (doseParts.length == 0) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT,
                "分次用量序列不能为空串");
        }
        for (int i = 0; i < doseParts.length; i++) {
            // 每段须可解析为正数（计量/拆零同判）
            this.requirePositiveScheduleSegment(doseParts[i], "分次剂量");
            this.requirePositiveScheduleSegment(quantityParts[i], "分次用量");
        }
    }

    /**
     * 解析 schedule 单段为正数；空白或非正拒绝。
     *
     * @param raw      逗号分割后的单段原文
     * @param fieldLabel 提示用字段名（分次剂量/分次用量）
     */
    private void requirePositiveScheduleSegment(String raw, String fieldLabel) {
        String trimmed = StringUtils.trimToEmpty(raw);
        if (StringUtils.isBlank(trimmed)) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT,
                fieldLabel + "各段不能为空");
        }
        BigDecimal value;
        try {
            value = new BigDecimal(trimmed);
        } catch (NumberFormatException ex) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT,
                fieldLabel + "各段须为数字");
        }
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT,
                fieldLabel + "各段须大于 0");
        }
    }

    /**
      * 校验当前操作的业务约束。
     */
    public void ensureDraftEditable() {
        if (!RequestStatus.DRAFT.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_STATUS, "仅草稿状态可编辑");
        }
    }

    /**
      * 校验当前操作的业务约束。
     */
    public void ensureSignable() {
        if (!RequestStatus.DRAFT.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_STATUS, "仅草稿状态可签发");
        }
    }

    /**
      * 校验当前操作的业务约束。
     */
    public void ensureSignOffAllowed() {
        if (!RequestStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_STATUS, "仅已发送状态可签退");
        }
    }

    /**
     * 断言可停嘱：须为长期且已开嘱校对（{@link RequestStatus#VERIFIED}）；禁止未校对直接停嘱。
     */
    public void ensureLongTermForStop() {
        if (!TherapyType.LONG_TERM.getCode().equals(this.therapyEnum)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT, "仅长期医嘱可停嘱");
        }
        // 未开嘱校对须走撤回，禁止 ACTIVE 直接停嘱。
        if (!RequestStatus.VERIFIED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_INVALID_STATUS, "仅已校对的长期医嘱可停嘱");
        }
    }

    /**
     * 断言当前为已停嘱的长期医嘱（改停嘱时间等；不检查停嘱校对痕迹）。
     */
    public void ensureLongTermStopped() {
        if (!TherapyType.LONG_TERM.getCode().equals(this.therapyEnum)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT, "仅长期医嘱可停嘱");
        }
        if (!RequestStatus.STOPPED.getCode().equals(this.statusEnum)) {
            // 非 STOPPED 不可操作停嘱轴，防止误改在执医嘱。
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_STATUS, "仅已停嘱的长期医嘱可操作停嘱");
        }
    }

    /**
     * 断言可取消停嘱：须为长期、已停嘱，且护士尚未完成停嘱校对（逆流程须先取消停嘱校对）。
     */
    public void ensureLongTermStoppedForCancelStop() {
        ensureLongTermStopped();
        // 已停嘱校对时医生禁止取消停嘱，须护士先取消停嘱校对。
        if (this.stopPerformerCheckId != null) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT, "已停嘱校对，请先取消停嘱校对后再取消停嘱");
        }
    }

    /**
     * 断言可停嘱校对：须已停嘱、长期、且尚未写入停嘱校对痕迹。
     */
    public void ensureStopVerifiable() {
        if (!TherapyType.LONG_TERM.getCode().equals(this.therapyEnum)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT, "仅长期医嘱可停嘱校对");
        }
        if (!RequestStatus.STOPPED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_STATUS, "仅已停嘱状态可停嘱校对");
        }
        // 已写过停嘱校对痕迹则拒绝重复校对（取消后可再校）。
        if (this.stopPerformerCheckId != null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT, "该医嘱已完成停嘱校对");
        }
    }

    /**
     * 断言可取消停嘱校对：须长期、已停嘱且已有停嘱校对痕迹。
     */
    public void ensureStopVerifyCancellable() {
        if (!TherapyType.LONG_TERM.getCode().equals(this.therapyEnum)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT, "仅长期医嘱可取消停嘱校对");
        }
        if (!RequestStatus.STOPPED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_STATUS, "仅已停嘱状态可取消停嘱校对");
        }
        // 未停嘱校对则无痕迹可清。
        if (this.stopPerformerCheckId == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT, "该医嘱尚未停嘱校对");
        }
    }

    /**
     * 断言可校对。
     */
    public void ensureVerifiable() {
        if (!RequestStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_STATUS, "仅已发送状态可校对");
        }
    }

    /**
     * 断言可退回（护士校对退回或医生对已校对未执行行签退）。
     *
     * <p>允许 {@link RequestStatus#ACTIVE} 与 {@link RequestStatus#VERIFIED}；
     * 后者对应住院医生站中医 IF-IP-TCM-38（BR-IP-TCM-009）。</p>
     */
    public void ensureRejectable() {
        if (!RequestStatus.ACTIVE.getCode().equals(this.statusEnum)
            && !RequestStatus.VERIFIED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_STATUS, "仅已发送或已校对状态可退回");
        }
    }

    /**
     * 断言可取消。
     */
    public void ensureCancellable() {
        if (!RequestStatus.ACTIVE.getCode().equals(this.statusEnum)
            && !RequestStatus.DRAFT.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_STATUS, "仅草稿或已发送状态可取消");
        }
    }

    /**
     * 断言仍为未签发草稿可废弃（调价配套；禁止误伤已签发医嘱）。
     *
     * @throws BusinessException 非 DRAFT 或已写入签发时刻时
     */
    public void ensureUnsignedDraftCancellable() {
        if (!RequestStatus.DRAFT.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_STATUS, "仅未签发草稿可废弃");
        }
        if (this.reqAuthoredTime != null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_STATUS, "已签发医嘱不可按草稿废弃");
        }
    }

    /**
     * 草稿发送至已发送：写入处方号并更新时间戳。
     *
     * @param prescriptionNo 处方号
     */
    public void draftToActive(String prescriptionNo) {
        this.prescriptionNo = prescriptionNo;
        this.statusEnum = RequestStatus.ACTIVE.getCode();
        this.statusChangedTime = LocalDateTime.now();
    }

    /**
      * 处理activeToDraft逻辑。
     */
    public void activeToDraft() {
        this.statusEnum = RequestStatus.DRAFT.getCode();
        this.statusChangedTime = LocalDateTime.now();
        // 签退/驳回回草稿：草稿态不得保留签发列（与 unify 脚本 DRAFT 口径一致）。
        this.reqAuthoredTime = null;
    }

    /**
     * 已发送转至已核对：校对人写入 {@code performer_check_id}，校对时刻写入 {@code check_time}（BR-MRQ-007）。
     *
     * <p>住院（病区核对）路径：状态落 {@link RequestStatus#VERIFIED}；不写 {@code stop_time}
     *（停嘱专用）。门诊收费「医嘱完成」请使用 {@link #activeToCompletedAfterOutpatientCharge()}。</p>
     *
     * @param checkerId 校对人主键（performer_check_id）
     * @param checkTime 校对时间（check_time）
     */
    public void activeToCompleted(Long checkerId, LocalDateTime checkTime) {
        this.performerCheckId = checkerId;
        this.checkTime = checkTime;
        this.statusEnum = RequestStatus.VERIFIED.getCode();
        this.statusChangedTime = LocalDateTime.now();
    }

    /**
     * 断言可由门诊收费链路推进为已完成（仅已发送态）。
     */
    public void ensureCompletableAfterOutpatientCharge() {
        if (!RequestStatus.ACTIVE.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_STATUS, "仅已发送状态允许门诊收费完成药品医嘱");
        }
    }

    /**
     * 门诊收费完成：已发送 → 已完成（不进住院核对 VERIFIED 语义）。
     */
    public void activeToCompletedAfterOutpatientCharge() {
        this.statusEnum = RequestStatus.COMPLETED.getCode();
        this.statusChangedTime = LocalDateTime.now();
    }

    /**
     * 停止用药请求：写入停嘱时间与停嘱人并迁至已停止；停嘱校对应为空（待护士校对）。
     *
     * @param stopTime  停嘱时间（写入 {@code stop_time}）
     * @param stopperId 停嘱人（写入 {@code stopper_id}；通常为操作医生）
     */
    public void toStopped(LocalDateTime stopTime, Long stopperId) {
        // 停嘱时间轴统一落 stop_time，不再占用服药计划结束列。
        this.stopTime = stopTime;
        // 停嘱人随停嘱操作落库，改停嘱时间不覆盖本列。
        this.stopperId = stopperId;
        // 新停嘱视为未停嘱校对，须护士重新校对。
        this.stopPerformerCheckId = null;
        this.stopCheckTime = null;
        this.statusEnum = RequestStatus.STOPPED.getCode();
        this.statusChangedTime = LocalDateTime.now();
    }

    /**
     * 取消停嘱：按开嘱校对痕迹恢复为已校对或已发送，并清空停嘱轴与停嘱校对痕迹（BR-IP-ORD-025 / BR-MRQ-006a）。
     */
    public void fromStopped() {
        // 护士已开嘱校对过则恢复 VERIFIED，否则恢复 ACTIVE（BR-IP-ORD-025 / BR-MRQ-006a）。
        if (this.performerCheckId != null) {
            this.statusEnum = RequestStatus.VERIFIED.getCode();
        } else {
            this.statusEnum = RequestStatus.ACTIVE.getCode();
        }
        // 取消停嘱清 stop_time / stopper_id / 停嘱校对痕迹，不改动服药计划结束列。
        this.stopTime = null;
        this.stopperId = null;
        this.stopPerformerCheckId = null;
        this.stopCheckTime = null;
        this.statusChangedTime = LocalDateTime.now();
    }

    /**
     * 停嘱校对：仅写停嘱校对人/时间，不改 {@link RequestStatus#STOPPED}。
     *
     * @param stopPerformerCheckId 停嘱校对人
     * @param stopCheckTime        停嘱校对时间
     */
    public void markStopChecked(Long stopPerformerCheckId, LocalDateTime stopCheckTime) {
        ensureStopVerifiable();
        // 校对人必填，禁止空痕迹。
        if (stopPerformerCheckId == null) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "停嘱校对人不能为空");
        }
        this.stopPerformerCheckId = stopPerformerCheckId;
        this.stopCheckTime = stopCheckTime;
    }

    /**
     * 取消停嘱校对：清空停嘱校对痕迹，状态仍为 {@link RequestStatus#STOPPED}。
     */
    public void clearStopChecked() {
        ensureStopVerifyCancellable();
        this.stopPerformerCheckId = null;
        this.stopCheckTime = null;
    }

    /**
     * 断言可改业务时间：开立任意存在行；停嘱须长期且已 STOPPED（BR-MRQ-006b）。
     *
     * @param timeKind 时间种类 code（{@link com.openhis.health.domain.share.enums.RequestBusinessTimeKind}）
     */
    public void ensureBusinessTimeChangeAllowed(String timeKind) {
        RequestBusinessTimeKind kind = RequestBusinessTimeKind.fromCodeOrNull(timeKind);
        // 种类非法或试图改签发：拒绝，签发列仅追溯。
        if (kind == null) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "不可修改该时间");
        }
        // 改停嘱时间仅已停嘱长期行可执行；已停嘱校对仍可改时且保留校对痕迹。
        if (RequestBusinessTimeKind.STOP.equals(kind)) {
            ensureLongTermStopped();
        }
    }

    /**
     * 改开立或停嘱时间；不改开立人/停嘱人/签发时间（BR-MRQ-006b）。
     *
     * @param timeKind 时间种类 code
     * @param newTime  新业务时间
     */
    public void changeBusinessTime(String timeKind, LocalDateTime newTime) {
        ensureBusinessTimeChangeAllowed(timeKind);
        // 新时间必填，避免把业务时间误清成空。
        if (newTime == null) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "新业务时间不能为空");
        }
        RequestBusinessTimeKind kind = RequestBusinessTimeKind.fromCodeOrNull(timeKind);
        // OPENED：仅覆盖开立列，开立人不变。
        if (RequestBusinessTimeKind.OPENED.equals(kind)) {
            this.reqOpenedTime = newTime;
            return;
        }
        // STOP：仅覆盖停嘱列，停嘱人不变。
        this.stopTime = newTime;
    }

    /**
      * 执行状态流转。
     */
    public void toCancelled() {
        this.statusEnum = RequestStatus.CANCELLED.getCode();
        this.statusChangedTime = LocalDateTime.now();
    }

    /**
     * 断言负向退药申请可在药房退药闭环后完结为已完成。
     */
    public void ensureCompletableAfterPharmacyRefund() {
        if (this.refundMedicineId == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT, "非退药负向申请不允许执行退药完结");
        }
        if (RequestStatus.COMPLETED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_STATUS, "退药申请已完成，请勿重复操作");
        }
    }

    /**
     * 断言本行为负向退药申请（含 {@code refund_medicine_id}），可供门诊退费申请撤销时软删。
     *
     * @throws BusinessException 非负向退药单时
     */
    public void ensureOpenRefundMedicationOrderForVoid() {
        if (this.refundMedicineId == null) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT,
                "非退药负向申请不允许作废");
        }
    }

    /**
     * 药房退药闭环完成：负向申请推进为已完成。
     */
    public void toCompletedAfterPharmacyRefund() {
        this.statusEnum = RequestStatus.COMPLETED.getCode();
        this.statusChangedTime = LocalDateTime.now();
    }

    /**
     * 逻辑删除药品申请。
     */
    public void markDeleted(Long operatorId) {
        this.setIsDeleted(Boolean.TRUE);
        this.setDeletedBy(operatorId);
        this.setDeletedAt(LocalDateTime.now());
    }

    /**
     * 退药负向单处方号：在原处方号前加 {@code T} 前缀（与耗材退负向单 BR-DR-007 同构）。
     *
     * @param sourcePrescriptionNo 原正向药嘱处方号
     */
    public void markAsRefundFromPrescription(String sourcePrescriptionNo) {
        if (StringUtils.isEmpty(sourcePrescriptionNo)) {
            return;
        }
        this.prescriptionNo = "T" + sourcePrescriptionNo.trim();
    }
}
