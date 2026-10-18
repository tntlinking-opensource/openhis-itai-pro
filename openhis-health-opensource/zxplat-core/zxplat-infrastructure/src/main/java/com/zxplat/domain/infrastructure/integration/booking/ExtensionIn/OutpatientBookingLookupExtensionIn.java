/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.booking.ExtensionIn;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 挂号台加载有效预约的检索条件（IF-REG-12）。
 *
 * <p>职责：承载患者、预约业务号与挂号日期三元组，供 {@code OutpatientBookingQueryExtension} 定位当日有效预约。</p>
 */
@Getter
@Setter
public class OutpatientBookingLookupExtensionIn {

    /** 患者主键 {@code adm_patient.id}。 */
    private Long patientId;

    /** 预约业务号（booking 侧展示号，可与患者组合唯一定位预约）。 */
    private String appointmentBusNo;

    /** 拟挂号日期；与预约 slot 日期对齐，用于过滤过期预约。 */
    private LocalDate registrationDate;
}
