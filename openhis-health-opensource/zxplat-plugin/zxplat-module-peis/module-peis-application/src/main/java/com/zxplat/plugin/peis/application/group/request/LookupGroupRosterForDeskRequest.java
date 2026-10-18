/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.request;

import lombok.Data;

/**
 * 登记台按患者查询团检名册上下文。
 */
@Data
public class LookupGroupRosterForDeskRequest {

    /** 患者主键。 */
    private Long patientId;
}
