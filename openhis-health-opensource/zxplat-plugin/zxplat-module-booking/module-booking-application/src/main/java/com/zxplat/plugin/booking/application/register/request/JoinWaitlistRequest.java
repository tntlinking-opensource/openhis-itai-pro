/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.register.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 加入候补队列（BR-EXT-011）。
 */
@Getter
@Setter
public class JoinWaitlistRequest {

    /** 机构/科室主键。 */
    @NotNull
    private Long organizationId;

    /** 患者主键。 */
    @NotNull
    private Long patientId;

    /** 已满号源主键（须处于可候补状态）。 */
    @NotNull
    private Long slotId;
}
