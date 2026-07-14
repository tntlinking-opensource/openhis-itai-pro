/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationrequest.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
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

    /** 诊断定义主键（列 {@code condition_definition_id}，即 {@code cln_condition_definition.id}）。 */
    @Column(name = "condition_definition_id")
    private Long conditionDefinitionId;

    @Column(name = "encounter_diagnosis_id")
    private Long encounterDiagnosisId;

    @Column(name = "performer_check_id")
    private Long performerCheckId;

    @Column(name = "check_time")
    private LocalDateTime checkTime;

    @Column(name = "effective_dose_end")
    private LocalDateTime effectiveDoseEnd;

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
     * 校验剂量语义。
     */
    public void validateDosageSemantics() {
        if (this.quantity == null || this.quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_ARGUMENT, "数量必须大于 0");
        }
        if (TherapyType.LONG_TERM.getCode().equals(this.therapyEnum)
            && this.firstDose != null && this.firstDose.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT, "长期医嘱首日量必须大于 0");
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
      * 校验当前操作的业务约束。
     */
    public void ensureLongTermForStop() {
        if (!TherapyType.LONG_TERM.getCode().equals(this.therapyEnum)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT, "仅长期医嘱可停嘱");
        }
    }

    /**
     * 断言可取消停嘱：须为长期且当前已停嘱。
     */
    public void ensureLongTermStoppedForCancelStop() {
        ensureLongTermForStop();
        if (!RequestStatus.STOPPED.getCode().equals(this.statusEnum)) {
            // 非 STOPPED 不可取消停嘱，防止误恢复在执医嘱。
            throw new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_INVALID_STATUS, "仅已停嘱的长期医嘱可取消停嘱");
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
    }

    /**
     * 已发送转至已核对：校对人写入 {@code performer_check_id}，校对时刻写入 {@code check_time}（BR-MRQ-007）。
     *
     * <p>住院（病区核对）路径：状态落 {@link RequestStatus#VERIFIED}；不写 {@code effective_dose_end}
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
     * 停止用药请求：写入有效结束时间并迁至已停止。
     *
     * @param stopTime 停止时间（写入有效剂量结束）
     */
    public void toStopped(LocalDateTime stopTime) {
        this.effectiveDoseEnd = stopTime;
        this.statusEnum = RequestStatus.STOPPED.getCode();
        this.statusChangedTime = LocalDateTime.now();
    }

    /**
     * 取消停嘱：按校对痕迹恢复为已校对或已发送，并清空服药结束时间（BR-IP-ORD-025 / BR-MRQ-006a）。
     */
    public void fromStopped() {
        // 护士已校对过则恢复 VERIFIED，否则恢复 ACTIVE（BR-IP-ORD-025 / BR-MRQ-006a）。
        if (this.performerCheckId != null) {
            this.statusEnum = RequestStatus.VERIFIED.getCode();
        } else {
            this.statusEnum = RequestStatus.ACTIVE.getCode();
        }
        this.effectiveDoseEnd = null;
        this.statusChangedTime = LocalDateTime.now();
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
