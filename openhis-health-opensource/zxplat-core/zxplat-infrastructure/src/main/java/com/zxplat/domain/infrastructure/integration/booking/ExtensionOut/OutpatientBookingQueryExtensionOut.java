/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.booking.ExtensionOut;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 门诊预约快照，供挂号台 IF-REG-12 预填。
 *
 * <p>职责：承载预约只读事实（科室/医师/时段/状态），health 不落库预约明细，仅消费展示与校验。</p>
 */
@Getter
@Setter
public class OutpatientBookingQueryExtensionOut {

    /** 预约主键。 */
    private Long appointmentId;

    /** 预约业务展示号。 */
    private String appointmentBusNo;

    /** 关联 slot 主键。 */
    private Long slotId;

    /** 预约科室主键。 */
    private Long organizationId;

    /** 预约医师主键。 */
    private Long practitionerId;

    /** 号源类型主键。 */
    private Long healthcareServiceId;

    /** 预约时段开始时间。 */
    private LocalDateTime slotStart;

    /** 预约时段结束时间。 */
    private LocalDateTime slotEnd;

    /** 科室名称（展示用）。 */
    private String organizationName;

    /** 医师姓名（展示用）。 */
    private String practitionerName;

    /** 号源类型名称（展示用）。 */
    private String serviceTypeName;

    /** 预约状态枚举 code（booking 侧，非字典 label）。 */
    private String statusEnum;

    /** 已转挂号时关联的就诊主键；未挂号则为空。 */
    private Long encounterId;
}
