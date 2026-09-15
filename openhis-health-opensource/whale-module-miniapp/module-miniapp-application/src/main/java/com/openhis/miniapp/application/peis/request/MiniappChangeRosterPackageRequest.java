/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.miniapp.application.peis.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 小程序：到检前换套餐。
 */
@Getter
@Setter
public class MiniappChangeRosterPackageRequest {

    /** 就诊人患者主键（会话未注入时必填）。 */
    private Long patientId;

    /** 名册主键。 */
    private Long rosterId;

    /** 新选定套餐 AD。 */
    private Long packageActivityId;
}
