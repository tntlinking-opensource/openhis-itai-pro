/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.miniapp.application.peis.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 小程序：按就诊人查询团检名册。
 */
@Getter
@Setter
public class MiniappLookupRosterRequest {

    /** 就诊人患者主键（会话未注入 patientId 时必填）。 */
    private Long patientId;
}
