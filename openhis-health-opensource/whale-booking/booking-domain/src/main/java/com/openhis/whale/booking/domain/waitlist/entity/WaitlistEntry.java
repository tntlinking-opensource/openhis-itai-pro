package com.openhis.whale.booking.domain.waitlist.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * 号源候补条目（bkg_waitlist_entry，BR-EXT-011）。
 */
@Getter
@Setter
@Entity
@Table(name = "bkg_waitlist_entry")
public class WaitlistEntry extends BaseAuditedEntity {

    /** 机构标识。 */
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    /** 患者主键。 */
    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    /** 候补号源主键。 */
    @Column(name = "slot_id", nullable = false)
    private Long slotId;

    /** 所属日程主键。 */
    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    /** 同 slot 下排队序号，从 1 递增。 */
    @Column(name = "queue_position", nullable = false)
    private Integer queuePosition;

    /** WAITING / PROMOTED / CANCELLED。 */
    @Column(name = "status", nullable = false)
    private String status;

    /** 提升为正式预约后关联 appointmentId。 */
    @Column(name = "promoted_appointment_id")
    private Long promotedAppointmentId;

    /** 取消原因。 */
    @Column(name = "cancel_reason")
    private String cancelReason;

    /** 乐观锁版本号。 */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version;
}
