package com.openhis.whale.booking.domain.healthcheckup.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 团检批次（bkg_group_checkup_batch）。
 */
@Getter
@Setter
@Entity
@Table(name = "bkg_group_checkup_batch")
public class GroupCheckupBatch extends BaseAuditedEntity {

    /** 团检单位主键。 */
    @Column(name = "org_id", nullable = false)
    private Long orgId;

    /** 批次名称。 */
    @Column(name = "batch_name", nullable = false)
    private String batchName;

    /** 默认套餐主键。 */
    @Column(name = "package_id")
    private Long packageId;

    /** 关联号源日程。 */
    @Column(name = "schedule_id")
    private Long scheduleId;

    /** 体检日期。 */
    @Column(name = "booking_date", nullable = false)
    private LocalDate bookingDate;

    /** 时段开始。 */
    @Column(name = "start_time")
    private LocalTime startTime;

    /** 时段结束。 */
    @Column(name = "end_time")
    private LocalTime endTime;

    /** 容量上限。 */
    @Column(name = "capacity_limit", nullable = false)
    private Integer capacityLimit = 0;

    /** 已预约人数。 */
    @Column(name = "booked_count", nullable = false)
    private Integer bookedCount = 0;

    /** DRAFT / OPEN / CLOSED 等。 */
    @Column(name = "status_code", nullable = false)
    private String statusCode = "DRAFT";
}
