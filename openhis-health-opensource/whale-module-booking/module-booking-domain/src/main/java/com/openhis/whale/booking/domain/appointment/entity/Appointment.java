/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.appointment.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 预约与候诊聚合根。
 *
 * <p>对应表：{@code wkf_appointment_queue}（表名沿用历史 wkf_ 前缀，非过渡双轨）。</p>
 *
 * <p>终态表达：采用软删 {@code isDeleted=true} + 原因字段（cancelReason/cancellationReasonCode）收口叫号/过号/取消等终态。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "wkf_appointment_queue")
public class Appointment extends BaseAuditedEntity {

    /** 预约分类（OUTPATIENT_BOOKING / CHECK_IN_ONLY）。 */
    @Column(name = "appointment_kind", nullable = false)
    private String appointmentKind;

    /** 业务号（可为空；生成策略由应用或领域策略决定）。 */
    @Column(name = "bus_no")
    private String busNo;

    /** 患者标识（门诊预约/小程序建议必填；可从 Encounter 解析）。 */
    @Column(name = "patient_id")
    private Long patientId;

    /** 就诊标识（候诊队列形态必填；纯预约阶段可空后填）。 */
    @Column(name = "encounter_id")
    private Long encounterId;

    /** 机构标识（机构维度隔离队列）。 */
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    /** 去向诊室/地点标识。 */
    @Column(name = "destination_location_id")
    private Long destinationLocationId;

    /** 分诊等级（文本）。 */
    @Column(name = "triage_level")
    private String triageLevel;

    /** 分诊优先级编码。 */
    @Column(name = "triage_priority_code")
    private String triagePriorityCode;

    /** 分诊时间。 */
    @Column(name = "triage_time")
    private LocalDateTime triageTime;

    /** 显示顺序（候诊队列形态必填语义）。 */
    @Column(name = "display_order")
    private Integer displayOrder;

    /** 号源标识（门诊预约）。 */
    @Column(name = "slot_id")
    private Long slotId;

    /** 冗余：医疗服务标识（门诊防重）。 */
    @Column(name = "healthcare_service_id")
    private Long healthcareServiceId;

    /** 冗余：预约自然日 Asia/Shanghai（门诊防重）。 */
    @Column(name = "booking_date")
    private LocalDate bookingDate;

    /** 乐观锁版本号。 */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    /** 服务类型编码。 */
    @Column(name = "service_type_code")
    private String serviceTypeCode;

    /** 预约类型编码。 */
    @Column(name = "appointment_type_code")
    private String appointmentTypeCode;

    /** 计划开始时间。 */
    @Column(name = "start_instant")
    private LocalDateTime startInstant;

    /** 计划结束时间。 */
    @Column(name = "end_instant")
    private LocalDateTime endInstant;

    /** 取消原因（文本）。 */
    @Column(name = "cancel_reason")
    private String cancelReason;

    /** 取消原因（编码）。 */
    @Column(name = "cancellation_reason_code")
    private String cancellationReasonCode;

    /** 取消/过号时间。 */
    @Column(name = "cancellation_at")
    private LocalDateTime cancellationAt;

    /** 最近叫号时间。 */
    @Column(name = "called_at")
    private LocalDateTime calledAt;

    /** 最近叫号医生标识。 */
    @Column(name = "last_practitioner_id")
    private Long lastPractitionerId;

    /** 渠道来源信息。 */
    @Embedded
    private AppointmentOrigin origin;

    /**
     * 校验分类与关键字段约束。
     *
     * <p>注意：本方法只覆盖 DM/SDD 明确的最小约束；更复杂的业务规则由应用服务在调用前完成跨聚合校验。</p>
     *
     * @throws BusinessException 参数非法
     */
    public void assertKindConstraints() {
        if (StringUtils.isBlank(this.appointmentKind)) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "预约分类 appointmentKind 不能为空");
        }
        if (this.organizationId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "机构标识 organizationId 不能为空");
        }

        if ("OUTPATIENT_BOOKING".equals(this.appointmentKind)) {
            if (this.patientId == null) {
                throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "门诊预约形态 patientId 不能为空");
            }
            if (this.slotId == null) {
                throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "门诊预约形态 slotId 不能为空");
            }
        }
    }

    /**
     * 标记终态并记录原因。
     *
     * @param reasonText 原因文本
     * @param reasonCode 原因编码
     * @param terminalAt 终态时间
     * @throws BusinessException 参数非法
     */
    public void markTerminalByReason(String reasonText, String reasonCode, LocalDateTime terminalAt) {
        if (StringUtils.isBlank(reasonText) && StringUtils.isBlank(reasonCode)) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "终态原因不能为空");
        }
        this.setIsDeleted(Boolean.TRUE);
        this.cancelReason = reasonText;
        this.cancellationReasonCode = reasonCode;
        this.cancellationAt = terminalAt;
    }

    /**
     * 校验当前记录为有效队列项（非终态）。
     *
     * @throws BusinessException 状态不允许
     */
    public void assertActive() {
        if (this.getIsDeleted() != null && this.getIsDeleted()) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "终态预约/候诊项不允许执行队列操作");
        }
    }
}
