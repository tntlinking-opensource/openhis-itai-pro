/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.bed.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 床位预约台初始化请求（8.1）。
 */
@Getter
@Setter
public class LoadBedBookingFormInitRequest {

    /** 机构/科室主键。 */
    private Long organizationId;
}
