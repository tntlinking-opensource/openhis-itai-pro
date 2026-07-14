/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.outpatient.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2201 门诊挂号登记结果 */
@Data
@Accessors(chain = true)
public class OpPlatformRegistrationResult {

    private String mdtrtId;
}
