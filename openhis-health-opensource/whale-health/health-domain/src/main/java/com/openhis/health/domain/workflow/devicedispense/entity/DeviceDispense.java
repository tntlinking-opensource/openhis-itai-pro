/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.devicedispense.entity;

import com.openhis.health.domain.share.constants.DoctorStationOrderCatalogConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 耗材发放聚合根。
 *
 * <p>对应表：wkf_device_dispense。</p>
 *
 * <p>边界：本聚合只维护发放事实与状态迁移；库存扣减/回补、追溯轨迹、收费联动由应用层编排。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "wkf_device_dispense")
public class DeviceDispense extends BaseAuditedEntity {

    @Column(name = "bus_no", nullable = false)
    private String busNo;

    @Column(name = "device_req_id", nullable = false)
    private Long deviceReqId;

    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    @Column(name = "based_on_table")
    private String basedOnTable;

    @Column(name = "based_on_id")
    private Long basedOnId;

    @Column(name = "not_performed_reason_enum")
    private String notPerformedReasonEnum;

    @Column(name = "dispense_category_enum")
    private String dispenseCategoryEnum;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit_code", nullable = false)
    private String unitCode;

    @Column(name = "lot_number")
    private String lotNumber;

    @Column(name = "device_def_id", nullable = false)
    private Long deviceDefId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "performer_id")
    private Long performerId;

    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "part_type_code")
    private String partTypeCode;

    @Column(name = "dispense_quantity")
    private BigDecimal dispenseQuantity;

    @Column(name = "preparer_id")
    private Long preparerId;

    @Column(name = "prepare_time")
    private LocalDateTime prepareTime;

    @Column(name = "dispense_time")
    private LocalDateTime dispenseTime;

    @Column(name = "limit_time")
    private LocalDateTime limitTime;

    @Column(name = "planned_dispense_time")
    private LocalDateTime plannedDispenseTime;

    @Column(name = "des_location_id")
    private String desLocationId;

    @Column(name = "rec_practitioner_id")
    private String recPractitionerId;

    @Column(name = "support_info")
    private String supportInfo;

    @Column(name = "usage_instruction")
    private String usageInstruction;

    @Column(name = "dispense_frequency_code")
    private String dispenseFrequencyCode;

    @Column(name = "trace_no", length = 50000)
    private String traceNo;

    @Column(name = "procedure_id")
    private Long procedureId;

    /** 乐观锁版本号。 */
    @jakarta.persistence.Version
    @Column(name = "version")
    private Integer version;

    /**
     * 私有方法：statusEquals。
     */
    private boolean statusEquals(DispenseStatus status) {
        return status.getCode().equals(this.statusEnum);
    }

    /**
     * 临时路径生成草稿前置（BR-DD-001）。
     */
    public void assertTemporaryGenerateAllowed() {
        if (this.quantity == null || this.quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "quantity 必须大于 0");
        }
    }

    /**
     * 草稿初始化；预减窗口 {@link DoctorStationOrderCatalogConstants#DISPENSE_RESERVE_DRAFT_LIMIT_HOURS} 小时。
     */
    public void initDraft() {
        LocalDateTime now = LocalDateTime.now();
        this.statusEnum = DispenseStatus.DRAFT.getCode();
        this.limitTime = now.plusHours(DoctorStationOrderCatalogConstants.DISPENSE_RESERVE_DRAFT_LIMIT_HOURS);
    }

    /**
     * 判断当前是否为草稿或待发状态（释放预减占用与 FIFO 签发替换时筛选可处理行，对齐 {@link com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense#isDraftOrPreparation()}）。
     *
     * @return {@code true} 表示当前为 DRAFT 或 PREPARATION
     */
    public boolean isDraftOrPreparation() {
        return statusEquals(DispenseStatus.DRAFT) || statusEquals(DispenseStatus.PREPARATION);
    }

    /**
     * 批量推进待发前置（DRAFT → PREPARATION）。
     */
    public void assertPromotable() {
        if (!statusEquals(DispenseStatus.DRAFT)) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_STATUS, "仅草稿状态允许推进为待发");
        }
    }

    /**
     * 草稿 → 待发；刷新预减窗口为 {@link DoctorStationOrderCatalogConstants#DISPENSE_RESERVE_PREPARATION_LIMIT_HOURS} 小时。
     */
    public void draftToPreparation() {
        assertPromotable();
        initPreparation();
    }

    /**
     * 执行路径生成前置：由调用方保证同请求上下文已存在可拷贝行。
     */
    public void assertExecutionGenerateAllowed() {
        if (this.deviceReqId == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "deviceReqId 不能为空");
        }
    }

    /**
     * 执行路径初始化（待发）；预减窗口 {@link DoctorStationOrderCatalogConstants#DISPENSE_RESERVE_PREPARATION_LIMIT_HOURS} 小时。
     */
    public void initPreparation() {
        LocalDateTime now = LocalDateTime.now();
        this.statusEnum = DispenseStatus.PREPARATION.getCode();
        this.limitTime = now.plusHours(DoctorStationOrderCatalogConstants.DISPENSE_RESERVE_PREPARATION_LIMIT_HOURS);
    }

    /**
     * 直发前置。
     */
    public void assertDirectIssueAllowed() {
        if (this.quantity == null || this.quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "quantity 必须大于 0");
        }
        if (this.unitCode == null || this.unitCode.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "unitCode 不能为空");
        }
        if (this.deviceDefId == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "deviceDefId 不能为空");
        }
    }

    /**
     * 直发初始化（已发放）。
     */
    public void initCompleted(Long performerId, BigDecimal dispenseQuantity, LocalDateTime now) {
        this.performerId = performerId;
        this.dispenseTime = now;
        this.dispenseQuantity = dispenseQuantity;
        this.statusEnum = DispenseStatus.COMPLETED.getCode();
    }

    /**
     * 待退记录创建前置（源单需已发放）。
     */
    public void assertPendingRefundCreatable(BigDecimal refundQuantity) {
        if (!statusEquals(DispenseStatus.COMPLETED) && !statusEquals(DispenseStatus.PART_COMPLETED)) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_STATUS, "仅已发放/部分发放允许创建待退记录");
        }
        if (this.dispenseQuantity == null || this.dispenseQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "源单已发数量无效");
        }
        if (refundQuantity == null || refundQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "refundQuantity 必须大于 0");
        }
        if (refundQuantity.compareTo(this.dispenseQuantity) > 0) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_RULE_CONFLICT, "退耗材数量不能大于已发数量");
        }
    }

    /**
     * 新行进入待退状态（由领域服务复制源发放后调用）。
     *
     * <p>保留源单签发 {@code lot_number}，供退库按原批回补；仅清空发放量与拣配/发放操作字段。</p>
     */
    public void toPendingRefund(BigDecimal refundQuantity) {
        this.quantity = refundQuantity;
        this.dispenseQuantity = null;
        // 保留 lot_number：退库须按原签发批号回补，禁止置空
        this.traceNo = null;
        this.prepareTime = null;
        this.dispenseTime = null;
        this.performerId = null;
        this.preparerId = null;
        this.statusEnum = DispenseStatus.PENDING_REFUND.getCode();
    }

    /**
     * 拣配前置（BR-DD-001）。
     */
    public void assertPreparable() {
        if (this.statusEnum == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "statusEnum 不能为空");
        }
        if (!statusEquals(DispenseStatus.PREPARATION)) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_STATUS, "仅待发状态允许拣配");
        }
        if (this.quantity == null || this.quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "quantity 必须大于 0");
        }
    }

    /**
     * 待发拣配 → 已配药。
     *
     * <p>传入批号为空时保留行上已有签发批号，禁止用空值覆盖。</p>
     */
    public void preparationToPreparedOrInProgress(Long preparerId, String lotNumber, String traceNo, BigDecimal quantity, LocalDateTime now) {
        this.preparerId = preparerId;
        // 有真实传入批号才覆盖；空则保留签发锁定值
        if (lotNumber != null && !lotNumber.trim().isEmpty()) {
            this.lotNumber = lotNumber.trim();
        }
        this.traceNo = traceNo;
        if (quantity != null) {
            this.quantity = quantity;
        }
        this.prepareTime = now;
        this.statusEnum = DispenseStatus.PREPARED.getCode();
    }

    /**
     * 发放前置（BR-DD-001/004）。
     */
    public void assertDispensable() {
        if (this.statusEnum == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "statusEnum 不能为空");
        }
        if (!statusEquals(DispenseStatus.PREPARED) && !statusEquals(DispenseStatus.IN_PROGRESS)) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_STATUS, "仅已配药/待发中状态允许发放");
        }
        if (this.deviceDefId == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "deviceDefId 不能为空");
        }
        if (this.unitCode == null || this.unitCode.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "unitCode 不能为空");
        }
    }

    /**
     * 核对发放 → 已发放/部分发放。
     */
    public void toCompletedOrPartCompleted(Long performerId, BigDecimal dispenseQuantity, LocalDateTime now) {
        this.performerId = performerId;
        this.dispenseTime = now;
        this.dispenseQuantity = dispenseQuantity;
        if (dispenseQuantity != null && this.quantity != null && dispenseQuantity.compareTo(this.quantity) < 0) {
            this.statusEnum = DispenseStatus.PART_COMPLETED.getCode();
        } else {
            this.statusEnum = DispenseStatus.COMPLETED.getCode();
        }
    }

    /**
     * 未发放作废前置。
     */
    public void assertCancellableUndispensed() {
        if (statusEquals(DispenseStatus.COMPLETED)
            || statusEquals(DispenseStatus.PART_COMPLETED)
            || statusEquals(DispenseStatus.REFUNDED)
            || statusEquals(DispenseStatus.DECLINED)
            || statusEquals(DispenseStatus.CANCELLED)
            || statusEquals(DispenseStatus.PENDING_REFUND)
            || statusEquals(DispenseStatus.SUMMARIZED)) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_STATUS, "当前状态不允许作废");
        }
    }

    /**
     * 未发放作废 → 拒绝。
     */
    public void toDeclined(String notPerformedReasonEnum) {
        assertCancellableUndispensed();
        this.notPerformedReasonEnum = notPerformedReasonEnum;
        this.statusEnum = DispenseStatus.DECLINED.getCode();
    }

    /**
     * 退耗材确认前置（待退行）。
     */
    public void assertRefundableAndIdempotent() {
        if (!statusEquals(DispenseStatus.PENDING_REFUND)) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_STATUS, "仅待退状态允许确认退费");
        }
    }

    /**
     * 待退 → 已退费。
     */
    public void pendingRefundToRefunded() {
        assertRefundableAndIdempotent();
        this.statusEnum = DispenseStatus.REFUNDED.getCode();
    }

    /**
     * 待退药拒绝：终态已拒绝，记录原因与操作人时刻。
     *
     * @param declineReasonText 拒绝原因
     * @param practitionerId    药师主键
     * @param now               操作时间
     */
    public void pendingRefundToDeclined(String declineReasonText, Long practitionerId, LocalDateTime now) {
        assertRefundableAndIdempotent();
        if (declineReasonText == null || declineReasonText.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "拒绝退药须填写原因");
        }
        this.notPerformedReasonEnum = declineReasonText.trim();
        this.performerId = practitionerId;
        this.dispenseTime = now;
        this.statusEnum = DispenseStatus.DECLINED.getCode();
    }

    /**
     * 待退确认并回写执行人与可选原因文本。
     *
     * @param practitionerId 执行人
     * @param now              操作时间
     * @param reasonText       可选退药原因
     */
    public void pendingRefundToRefundedWithAudit(Long practitionerId, LocalDateTime now, String reasonText) {
        assertRefundableAndIdempotent();
        this.performerId = practitionerId;
        this.dispenseTime = now;
        if (reasonText != null && !reasonText.trim().isEmpty()) {
            this.supportInfo = reasonText.trim();
        }
        this.statusEnum = DispenseStatus.REFUNDED.getCode();
    }

    /**
     * 预减释放前置。
     */
    public void assertReservableReleased() {
        if (!statusEquals(DispenseStatus.PREPARATION) && !statusEquals(DispenseStatus.DRAFT) && !statusEquals(DispenseStatus.PREPARED)) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_STATUS, "当前状态不允许释放预减");
        }
    }

    /**
     * 预减释放 → 停发。
     */
    public void toStoppedOrDeleted(String notPerformedReasonEnum) {
        assertReservableReleased();
        this.notPerformedReasonEnum = notPerformedReasonEnum;
        this.statusEnum = DispenseStatus.STOPPED.getCode();
    }

    /**
     * 批号匹配前置。
     */
    public void assertLotMatchable() {
        if (!statusEquals(DispenseStatus.PREPARATION)) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_STATUS, "仅待发状态允许批号匹配门控");
        }
    }

    /**
     * 批号匹配不改变状态（占位：库存/FEFO 由应用编排）。
     */
    public void keepPreparation() {
        assertLotMatchable();
    }

    /**
     * 汇总落账前置。
     */
    public void assertSummarizable() {
        if (!statusEquals(DispenseStatus.COMPLETED) && !statusEquals(DispenseStatus.PART_COMPLETED)) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_STATUS, "仅已发放/部分发放允许汇总");
        }
    }

    /**
     * → 已汇总。
     */
    public void toSummarized(String summaryAnchor) {
        assertSummarizable();
        this.supportInfo = summaryAnchor;
        this.statusEnum = DispenseStatus.SUMMARIZED.getCode();
    }

    /**
     * 汇总完成回写前置。
     */
    public void assertSummaryCompletable() {
        if (!statusEquals(DispenseStatus.SUMMARIZED)) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_STATUS, "仅已汇总状态允许回写完成");
        }
    }

    /**
     * 已汇总 → 已发放。
     */
    public void summarizedToCompleted() {
        assertSummaryCompletable();
        this.statusEnum = DispenseStatus.COMPLETED.getCode();
    }

    /**
     * 汇总取消前置。
     */
    public void assertSummaryCancellable() {
        if (!statusEquals(DispenseStatus.SUMMARIZED)) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_STATUS, "仅已汇总状态允许取消汇总");
        }
    }

    /**
     * 已汇总 → 待发。
     */
    public void summarizedToPreparation() {
        assertSummaryCancellable();
        this.supportInfo = null;
        this.statusEnum = DispenseStatus.PREPARATION.getCode();
    }

    /**
     * 未发放回退前置。
     */
    public void assertRollbackableUndispensed() {
        if (!statusEquals(DispenseStatus.PREPARATION)
            && !statusEquals(DispenseStatus.PREPARED)
            && !statusEquals(DispenseStatus.IN_PROGRESS)
            && !statusEquals(DispenseStatus.ON_HOLD)) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_STATUS, "当前状态不允许回退为草稿");
        }
    }

    /**
     * → 草稿：清空拣配操作字段，保留已锁定批号（若有）。
     *
     * <p>未发回退草稿后若再签发，FIFO 会软删重建发放行；此处不置空批号，避免中间态丢批。</p>
     */
    public void toDraft() {
        assertRollbackableUndispensed();
        // 保留 lot_number：禁止回退路径清签发批号
        this.traceNo = null;
        this.prepareTime = null;
        this.preparerId = null;
        this.statusEnum = DispenseStatus.DRAFT.getCode();
    }

    /**
     * 未发放删除前置。
     */
    public void assertRemovableUndispensed() {
        if (statusEquals(DispenseStatus.COMPLETED)
            || statusEquals(DispenseStatus.PART_COMPLETED)
            || statusEquals(DispenseStatus.REFUNDED)
            || statusEquals(DispenseStatus.SUMMARIZED)) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_STATUS, "已发放或已汇总记录不可删除");
        }
    }

    /**
     * 逻辑删除发放记录（签退释占、FIFO 签发替换与未发删除路径，对齐药品 {@code markDeleted}）。
     *
     * @param operatorId 操作人；可空
     */
    public void markDeleted(Long operatorId) {
        assertRemovableUndispensed();
        this.setIsDeleted(Boolean.TRUE);
        this.setDeletedBy(operatorId);
        this.setDeletedAt(LocalDateTime.now());
    }

    /**
     * 逻辑删除（无操作人审计字段）。
     */
    public void logicalDelete() {
        this.markDeleted(null);
    }

    /**
     * 停发前置。
     */
    public void assertStoppableUndispensed() {
        if (statusEquals(DispenseStatus.COMPLETED)
            || statusEquals(DispenseStatus.PART_COMPLETED)
            || statusEquals(DispenseStatus.REFUNDED)
            || statusEquals(DispenseStatus.DECLINED)
            || statusEquals(DispenseStatus.CANCELLED)
            || statusEquals(DispenseStatus.STOPPED)) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_STATUS, "当前状态不允许停发");
        }
    }

    /**
     * → 停发。
     */
    public void toStopped(String notPerformedReasonEnum) {
        assertStoppableUndispensed();
        this.notPerformedReasonEnum = notPerformedReasonEnum;
        this.statusEnum = DispenseStatus.STOPPED.getCode();
    }

    /**
     * 挂起前置。
     */
    public void assertHoldable() {
        if (!statusEquals(DispenseStatus.PREPARATION) && !statusEquals(DispenseStatus.PREPARED) && !statusEquals(DispenseStatus.IN_PROGRESS)) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_STATUS, "当前状态不允许挂起");
        }
    }

    /**
     * → 挂起。
     */
    public void toOnHold(String reason) {
        assertHoldable();
        if (reason != null && !reason.isBlank()) {
            this.supportInfo = reason;
        }
        this.statusEnum = DispenseStatus.ON_HOLD.getCode();
    }

    /**
     * 撤回已发放前置。
     */
    public void assertRecallable() {
        if (!statusEquals(DispenseStatus.COMPLETED) && !statusEquals(DispenseStatus.PART_COMPLETED)) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_STATUS, "仅已发放/部分发放允许撤回");
        }
    }

    /**
     * 已发放 → 作废（撤回）。
     */
    public void completedToCancelled(String reason) {
        assertRecallable();
        if (reason != null && !reason.isBlank()) {
            this.supportInfo = reason;
        }
        this.statusEnum = DispenseStatus.CANCELLED.getCode();
    }

    /**
     * 批量回退已配药 → 待发前置。
     */
    public void assertPreparedBatchRevertable() {
        if (!statusEquals(DispenseStatus.PREPARED)) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_STATUS, "仅已配药状态允许回退为待发");
        }
    }

    /**
     * 已配药 → 待发：清空拣配人/时间/追溯码，保留医生签发批号（BR-DD-009 / BR-NUR-007）。
     *
     * <p>门诊护士取消执行不重跑 FIFO、不改批号；仅允许换库房。批号须全程沿用签发锁定值。</p>
     */
    public void preparedToPreparation() {
        this.assertPreparedBatchRevertable();
        // 保留 lot_number：签发 FIFO 锁定批号，取消执行禁止置空
        this.traceNo = null;
        this.prepareTime = null;
        this.preparerId = null;
        this.statusEnum = DispenseStatus.PREPARATION.getCode();
    }

    /**
     * 批量回退已发放 → 待发前置。
     */
    public void assertCompletedBatchRevertable() {
        if (!statusEquals(DispenseStatus.COMPLETED) && !statusEquals(DispenseStatus.PART_COMPLETED)) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_STATUS, "仅已发放/部分发放允许回退为待发");
        }
    }

    /**
     * 已发放 → 待发：清空发放量/发放人/时间/追溯码，保留医生签发批号（BR-DD-009 / BR-NUR-007）。
     *
     * <p>与护士换房口径一致：批号不变；库存回补仍按回退前快照的签发批号+库房执行。</p>
     */
    public void completedToPreparation() {
        assertCompletedBatchRevertable();
        // 保留 lot_number：签发 FIFO 锁定批号，取消执行禁止置空
        this.traceNo = null;
        this.dispenseQuantity = null;
        this.dispenseTime = null;
        this.performerId = null;
        this.statusEnum = DispenseStatus.PREPARATION.getCode();
    }

    /**
     * 修正客户可见发放时刻（发放时间轴 = recorded 轴；不改 planned_dispense_time）。
     *
     * <p>已写 {@code dispense_time} 则改发放时刻；否则改 {@code prepare_time}（仅拣配未发）。</p>
     *
     * @param newDisplayTime 新的展示时刻
     * @throws BusinessException 时刻为空或无可改展示字段
     */
    public void amendDisplayTime(LocalDateTime newDisplayTime) {
        if (newDisplayTime == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "发放展示时间不能为空");
        }
        // 已发优先改 dispense_time（客户可见发放时间）
        if (this.dispenseTime != null) {
            this.dispenseTime = newDisplayTime;
            return;
        }
        // 仅拣配：改 prepare_time
        if (this.prepareTime != null) {
            this.prepareTime = newDisplayTime;
            return;
        }
        throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_STATUS, "发放单尚无拣配/发放时间，无法修改");
    }
}
