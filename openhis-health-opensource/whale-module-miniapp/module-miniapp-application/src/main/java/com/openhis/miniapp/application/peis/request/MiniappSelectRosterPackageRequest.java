/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.miniapp.application.peis.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 小程序：选定套餐并建单。
 */
@Getter
@Setter
public class MiniappSelectRosterPackageRequest {

    /** 就诊人患者主键（会话未注入时必填）。 */
    private Long patientId;

    /** 名册主键。 */
    private Long rosterId;

    /** 选定套餐 AD。 */
    private Long packageActivityId;

    /** 机构覆盖（可选）。 */
    private Long orgId;
}
