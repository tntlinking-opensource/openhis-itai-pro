/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.booking.ExtensionOut;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 住院床位预约快照（health 集成层）。
 *
 * <p>职责：护士站分床前只读查询 booking 侧有效床位预约，用于校验患者与拟入院日期。</p>
 */
@Getter
@Setter
public class InpatientBedReservationQueryExtensionOut {

    /** 床位预约主键。 */
    private Long appointmentId;

    /** 目标床位位置主键 {@code adm_location.id}。 */
    private Long bedLocationId;

    /** 预约患者主键。 */
    private Long patientId;

    /** 拟入院日期，分床当日须与此日期匹配或在其策略窗口内。 */
    private LocalDate intendedAdmissionDate;
}
