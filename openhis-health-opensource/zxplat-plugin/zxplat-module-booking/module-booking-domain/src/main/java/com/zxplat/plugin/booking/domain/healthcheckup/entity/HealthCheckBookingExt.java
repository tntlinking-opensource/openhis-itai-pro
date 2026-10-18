/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.healthcheckup.entity;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 体检预约扩展（bkg_health_check_booking_ext）。
 */
@Getter
@Setter
@Entity
@Table(name = "bkg_health_check_booking_ext")
public class HealthCheckBookingExt extends BaseAuditedEntity {

    /** 关联预约主键。 */
    @Column(name = "appointment_id", nullable = false)
    private Long appointmentId;

    /** 套餐主键。 */
    @Column(name = "package_id")
    private Long packageId;

    /** 团检批次主键（个检为空）。 */
    @Column(name = "batch_id")
    private Long batchId;

    /** 下单时套餐快照 JSON。 */
    @Column(name = "package_snapshot_json")
    private String packageSnapshotJson;

    /** 参考金额。 */
    @Column(name = "estimated_amount")
    private BigDecimal estimatedAmount;

    /** 团检合同主键；散客可空。 */
    @Column(name = "contract_id")
    private Long contractId;

    /** 进池合同类型编码。 */
    @Column(name = "contract_type_code")
    private String contractTypeCode;

    /** 校区编码；无校区模块可空。 */
    @Column(name = "campus_code")
    private String campusCode;

    /** 占用的 bkg_hc_day_slot 主键。 */
    @Column(name = "day_slot_id")
    private Long daySlotId;
}
