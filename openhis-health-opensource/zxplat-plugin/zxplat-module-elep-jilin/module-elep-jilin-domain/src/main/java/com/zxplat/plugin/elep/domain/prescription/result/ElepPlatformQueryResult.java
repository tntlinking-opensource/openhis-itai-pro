/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.domain.prescription.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 【A0005】平台查询结果（Bridge）。 */
@Data
@Accessors(chain = true)
public class ElepPlatformQueryResult {

    private boolean success;
    private String message;
    private String rxStasCodg;
    private String rxUsedStasCodg;
    private String platformJson;
}
