/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdispense.entity;

import com.openhis.health.domain.share.constants.DoctorStationOrderCatalogConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.MedicationDispenseType;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import com.openhis.health.domain.share.converter.DispenseStatusConverter;
import com.openhis.health.domain.share.converter.MedicationDispenseTypeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.openhis.whale.core.application.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 药品发放聚合根。
 */
@Getter
@Setter
@Entity
@Table(name = "med_medication_dispense")
public class MedicationDispense extends BaseAuditedEntity {

    /** bus 编号。 */
    @Column(name = "bus_no", nullable = false)
    private String busNo;

    /** status 枚举值。 */
    @Column(name = "status_enum", nullable = false)
    @Convert(converter = DispenseStatusConverter.class)
    private DispenseStatus statusEnum;

    /** notPerformedReason 枚举值。 */
    @Column(name = "not_performed_reason_enum")
    private String notPerformedReasonEnum;

    /** statusChanged 时间。 */
    @Column(name = "status_changed_time")
    private LocalDateTime statusChangedTime;

    /** dispense 枚举值。 */
    @Column(name = "dispense_enum", nullable = false)
    @Convert(converter = MedicationDispenseTypeConverter.class)
    private MedicationDispenseType dispenseEnum;

    /** medication 标识。 */
    @Column(name = "medication_id", nullable = false)
    private Long medicationId;

    /** patient 标识。 */
    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    /** encounter 标识。 */
    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    /** location 标识。 */
    @Column(name = "location_id", nullable = false)
    private Long locationId;

    /** medReq 标识。 */
    @Column(name = "med_req_id", nullable = false)
    private Long medReqId;

    /** quantity。 */
    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    /** unit 代码。 */
    @Column(name = "unit_code", nullable = false)
    private String unitCode;

    /** dispenseQuantity。 */
    @Column(name = "dispense_quantity")
    private BigDecimal dispenseQuantity;

    /** lotNumber。 */
    @Column(name = "lot_number")
    private String lotNumber;

    /** trace 编号。 */
    @Column(name = "trace_no", length = 50000)
    private String traceNo;

    /** preparer 标识。 */
    @Column(name = "preparer_id")
    private Long preparerId;

    /** prepare 时间。 */
    @Column(name = "prepare_time")
    private LocalDateTime prepareTime;

    /** practitioner 标识。 */
    @Column(name = "practitioner_id")
    private Long practitionerId;

    /** dispense 时间。 */
    @Column(name = "dispense_time")
    private LocalDateTime dispenseTime;

    /** limit 时间。 */
    @Column(name = "limit_time")
    private LocalDateTime limitTime;

    /** plannedDispense 时间。 */
    @Column(name = "planned_dispense_time")
    private LocalDateTime plannedDispenseTime;

    /** procedure 标识。 */
    @Column(name = "procedure_id")
    private Long procedureId;

    /** summary 编号。 */
    @Column(name = "summary_no")
    private String summaryNo;

    /** desLocation 标识。 */
    @Column(name = "des_location_id")
    private Long desLocationId;

    /** recPractitioner 标识。 */
    @Column(name = "rec_practitioner_id")
    private Long recPractitionerId;

    /** dosageInstruction。 */
    @Column(name = "dosage_instruction")
    private String dosageInstruction;

    /** method 代码。 */
    @Column(name = "method_code")
    private String methodCode;

    /** frequency 代码。 */
    @Column(name = "frequency_code")
    private String frequencyCode;

    /** dose。 */
    @Column(name = "dose")
    private BigDecimal dose;

    /** doseUnit 代码。 */
    @Column(name = "dose_unit_code")
    private String doseUnitCode;

    /** maxUnit。 */
    @Column(name = "max_unit")
    private BigDecimal maxUnit;

    /** supportInfo。 */
    @Column(name = "support_info")
    private String supportInfo;

    /** partType 代码。 */
    @Column(name = "part_type_code")
    private String partTypeCode;

    /** dispenseFrequency 代码。 */
    @Column(name = "dispense_frequency_code")
    private String dispenseFrequencyCode;

    /** 乐观锁版本号。 */
    @jakarta.persistence.Version
    @Column(name = "version")
    private Integer version;

    /**
     * 判断当前是否为草稿状态（用于批量推进逻辑中筛选可推进的发放单）。
     *
     * @return true 表示当前为 DRAFT 草稿状态
     */
    public boolean isDraftStatus() {
        return DispenseStatus.DRAFT.equals(this.statusEnum);
    }

    /**
     * 判断当前是否为草稿或待配药状态（用于批量释放预减占用逻辑中筛选可释放的发放单）。
     *
     * @return true 表示当前为 DRAFT 或 PREPARATION 状态
     */
    public boolean isDraftOrPreparation() {
        return DispenseStatus.DRAFT.equals(this.statusEnum)
            || DispenseStatus.PREPARATION.equals(this.statusEnum);
    }

    /**
     * 初始化草稿；预减窗口 {@link DoctorStationOrderCatalogConstants#DISPENSE_RESERVE_DRAFT_LIMIT_HOURS} 小时。
     */
    public void initDraft() {
        LocalDateTime now = LocalDateTime.now();
        this.statusEnum = DispenseStatus.DRAFT;
        this.statusChangedTime = now;
        this.limitTime = now.plusHours(DoctorStationOrderCatalogConstants.DISPENSE_RESERVE_DRAFT_LIMIT_HOURS);
    }

    /**
     * 初始化待配药；预减窗口 {@link DoctorStationOrderCatalogConstants#DISPENSE_RESERVE_PREPARATION_LIMIT_HOURS} 小时。
     */
    public void initPreparation() {
        LocalDateTime now = LocalDateTime.now();
        this.statusEnum = DispenseStatus.PREPARATION;
        this.statusChangedTime = now;
        this.limitTime = now.plusHours(DoctorStationOrderCatalogConstants.DISPENSE_RESERVE_PREPARATION_LIMIT_HOURS);
    }

    /**
     * 断言可配药。
     */
    public void ensureStatusForPrepare() {
        if (!DispenseStatus.PREPARATION.equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_STATUS, "仅待配药状态可执行配药");
        }
    }

    /**
     * 断言可发药。
     */
    public void ensureStatusForDispense() {
        if (!DispenseStatus.PREPARED.equals(this.statusEnum)
            && !DispenseStatus.IN_PROGRESS.equals(this.statusEnum)
            && !DispenseStatus.SUMMARIZED.equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_STATUS, "仅待发药/已配药/已汇总状态可执行发药");
        }
    }

    /**
     * 断言可退药（含已发完、部分发、待退药窗口）。
     */
    public void ensureRefundable() {
        if (!DispenseStatus.COMPLETED.equals(this.statusEnum)
            && !DispenseStatus.PART_COMPLETED.equals(this.statusEnum)
            && !DispenseStatus.PENDING_REFUND.equals(this.statusEnum)) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_STATUS,
                "仅已发药、部分发药或待退药状态可发起/延续退药"
            );
        }
    }

    /**
     * 断言可作废。
     */
    public void ensureCancellable() {
        if (!DispenseStatus.DRAFT.equals(this.statusEnum)
            && !DispenseStatus.PREPARATION.equals(this.statusEnum)
            && !DispenseStatus.PREPARED.equals(this.statusEnum)
            && !DispenseStatus.IN_PROGRESS.equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_STATUS, "仅未发药记录允许作废");
        }
    }

    /**
     * 校验库存五元组。
     */
    public void validateInventoryTuple() {
        if (this.medicationId == null || this.locationId == null || this.quantity == null || StringUtils.isEmpty(this.unitCode)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "库存扣减五元组不完整");
        }
    }

    /**
     * 校验追溯码。
     */
    public void validateTraceCode() {
        if (StringUtils.isEmpty(this.traceNo)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_RULE_CONFLICT, "发药必须记录追溯码");
        }
    }

    /**
     * 断言汇总核发后的发放行可接受护士站追溯码分配（**AS-IP-NUR-015 · BR-IP-TRC-002/009**）。
     *
     * <p>须 {@link DispenseStatus#COMPLETED} 且 {@code summary_no} 非空；不校验 trace 是否已填（允许覆盖）。</p>
     */
    public void ensureTraceAssignableOnCompleted() {
        if (!DispenseStatus.COMPLETED.equals(this.statusEnum)) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_STATUS,
                "仅已核发发放行可分配追溯码");
        }
        if (StringUtils.isEmpty(this.summaryNo)) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_DISPENSE_RULE_CONFLICT,
                "非汇总路径发放行不可在本场景分配追溯码");
        }
    }

    /**
     * 仅回写追溯码，不迁移发放状态、不触发发药/追溯 OUT 事件（**AS-IP-NUR-015 · BR-IP-TRC-009**）。
     *
     * @param traceNo 逗号分隔追溯码串；空白则跳过
     */
    public void applyTraceNoOnly(String traceNo) {
        if (StringUtils.isEmpty(traceNo)) {
            return;
        }
        this.traceNo = traceNo.trim();
    }

    /**
     * 配药完成：进入待发药（{@link DispenseStatus#IN_PROGRESS}），与 AS-OP-PHA-001「保存配药」口径一致。
     *
     * @param preparerId 调配药师主键
     */
    public void toPrepared(Long preparerId) {
        this.preparerId = preparerId;
        this.prepareTime = LocalDateTime.now();
        this.statusEnum = DispenseStatus.IN_PROGRESS;
        this.statusChangedTime = this.prepareTime;
    }

    /**
     * 迁移到已发放。
     */
    public void toCompleted(Long practitionerId, BigDecimal actualQuantity) {
        this.practitionerId = practitionerId;
        this.dispenseQuantity = actualQuantity;
        this.dispenseTime = LocalDateTime.now();
        this.statusEnum = DispenseStatus.COMPLETED;
        this.statusChangedTime = this.dispenseTime;
    }

    /**
     * 汇总核发闭环：在 {@link DispenseStatus#SUMMARIZED} 中间态下一并写入配药/发药人与时刻，推进为终态 {@link DispenseStatus#COMPLETED}。
     *
     * <p>是否汇总路径以 {@code summary_no} 非空识别；{@code SUMMARIZED} 仅表达待发核发，**不是**业务终态（终态以本表 {@code COMPLETED} 为准）。</p>
     *
     * @param dispenserId     发药药师主键
     * @param preparerId      调配药师主键
     * @param actualQuantity  实发数量；空则回退 {@link #quantity}
     * @param completedTime   配药/发药完成时刻（同刻写入 prepareTime 与 dispenseTime）
     */
    public void completeSummaryDispenseFacts(
        Long dispenserId,
        Long preparerId,
        BigDecimal actualQuantity,
        LocalDateTime completedTime) {
        this.ensureStatusForDispense();
        // 汇总路径必须以 summary_no 识别，防止按患者发药行误入本方法
        if (StringUtils.isEmpty(this.summaryNo)) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_DISPENSE_RULE_CONFLICT,
                "非汇总路径发放行不可执行汇总核发");
        }
        if (preparerId == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "调配药师不能为空");
        }
        if (dispenserId == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "发药人不能为空");
        }
        if (completedTime == null) {
            completedTime = LocalDateTime.now();
        }
        BigDecimal resolvedQuantity = actualQuantity;
        if (resolvedQuantity == null) {
            resolvedQuantity = this.quantity;
        }
        this.preparerId = preparerId;
        this.prepareTime = completedTime;
        this.practitionerId = dispenserId;
        this.dispenseQuantity = resolvedQuantity;
        this.dispenseTime = completedTime;
        this.statusEnum = DispenseStatus.COMPLETED;
        this.statusChangedTime = completedTime;
    }

    /**
     * 迁移到已拒绝。
     */
    public void toDeclined(String reasonEnum) {
        if (reasonEnum == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "作废时必须填写未执行原因");
        }
        this.notPerformedReasonEnum = reasonEnum;
        this.statusEnum = DispenseStatus.DECLINED;
        this.statusChangedTime = LocalDateTime.now();
    }

    /**
     * 迁移到已退药。
     */
    public void toRefunded() {
        this.statusEnum = DispenseStatus.REFUNDED;
        this.statusChangedTime = LocalDateTime.now();
    }

    /**
     * 迁移到已退药并记录退药数量。
     */
    public void toRefunded(BigDecimal refundQuantity) {
        this.dispenseQuantity = refundQuantity;
        this.statusEnum = DispenseStatus.REFUNDED;
        this.statusChangedTime = LocalDateTime.now();
    }

    /**
     * 迁移到待退药。
     */
    public void toPendingRefund() {
        this.statusEnum = DispenseStatus.PENDING_REFUND;
        this.statusChangedTime = LocalDateTime.now();
    }

    /**
     * 断言当前为待退药态，允许药房拒绝退药。
     */
    public void ensurePendingRefundForPharmacyDecline() {
        if (!DispenseStatus.PENDING_REFUND.equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_STATUS, "仅待退药状态允许拒绝退药");
        }
    }

    /**
     * 待退药拒绝：进入已拒绝终态并记录原因与操作人时刻。
     *
     * @param declineReasonText 拒绝原因（手写）
     * @param practitionerId    药师主键
     */
    public void pendingRefundToDeclined(String declineReasonText, Long practitionerId) {
        this.ensurePendingRefundForPharmacyDecline();
        if (declineReasonText == null || declineReasonText.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "拒绝退药须填写原因");
        }
        this.notPerformedReasonEnum = declineReasonText.trim();
        this.practitionerId = practitionerId;
        this.dispenseTime = LocalDateTime.now();
        this.statusEnum = DispenseStatus.DECLINED;
        this.statusChangedTime = this.dispenseTime;
    }

    /**
     * 药房确认退药：在待退药态下推进为已退药，并回写实退数量、操作人与可选原因。
     *
     * @param refundQuantity  实退数量（全量退回时与开立数量一致）
     * @param practitionerId  药师主键
     * @param reasonText      可选退药原因（写入 {@code support_info}）
     */
    public void pendingRefundToRefundedWithAudit(BigDecimal refundQuantity, Long practitionerId, String reasonText) {
        if (!DispenseStatus.PENDING_REFUND.equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_STATUS, "仅待退药状态允许确认退药");
        }
        if (refundQuantity == null || refundQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "退药数量必须大于 0");
        }
        this.dispenseQuantity = refundQuantity;
        this.practitionerId = practitionerId;
        this.dispenseTime = LocalDateTime.now();
        if (reasonText != null && !reasonText.trim().isEmpty()) {
            this.supportInfo = reasonText.trim();
        }
        this.statusEnum = DispenseStatus.REFUNDED;
        this.statusChangedTime = this.dispenseTime;
    }

    /**
     * 应用汇总状态。
     */
    public void applySummaryState(String summaryNo) {
        this.summaryNo = summaryNo;
        this.statusEnum = DispenseStatus.SUMMARIZED;
        this.statusChangedTime = LocalDateTime.now();
    }

    /**
     * 断言可取消汇总。
     */
    public void ensureSummaryCancellable() {
        if (!DispenseStatus.SUMMARIZED.equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_STATUS, "仅已汇总记录允许取消汇总");
        }
    }

    /**
     * 断言可回退到草稿。
     */
    public void ensureRollbackable() {
        if (!DispenseStatus.PREPARATION.equals(this.statusEnum)
            && !DispenseStatus.PREPARED.equals(this.statusEnum)
            && !DispenseStatus.SUMMARIZED.equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_STATUS, "仅未发药流程中的记录允许回退");
        }
    }

    /**
     * 逻辑删除发放记录。
     */
    public void markDeleted(Long operatorId) {
        this.setIsDeleted(Boolean.TRUE);
        this.setDeletedBy(operatorId);
        this.setDeletedAt(LocalDateTime.now());
    }

    /**
     * 断言发放行未被软删（取消执行释占、签退替换等路径；GAP-STALE-003）。
     */
    public void assertNotDeleted() {
        if (Boolean.TRUE.equals(this.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_NOT_FOUND, "药品发放记录不存在");
        }
    }
}
