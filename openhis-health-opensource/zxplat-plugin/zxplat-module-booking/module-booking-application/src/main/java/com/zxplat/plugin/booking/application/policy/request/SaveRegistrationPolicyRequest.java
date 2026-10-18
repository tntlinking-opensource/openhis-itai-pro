/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.policy.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存院点挂号策略请求。
 */
@Getter
@Setter
public class SaveRegistrationPolicyRequest {

    /** 主键（更新时）。 */
    private Long id;

    /** 科室/机构主键。 */
    @NotNull
    private Long organizationId;

    /** 现场挂号号源模式（如 APPOINTMENT_FIRST）。 */
    @NotBlank
    private String walkInSlotMode;

    /** 挂号时是否自动报到入队。 */
    @NotNull
    private Boolean autoCheckInOnRegister;

    /** 退号处置策略（如 RELEASE_SLOT_ONLY / CANCEL_APPOINTMENT）。 */
    @NotBlank
    private String onCancelPolicy;

    /** 是否允许从预约修改接诊医师。 */
    @NotNull
    private Boolean allowEditPractitionerFromAppointment;
}
