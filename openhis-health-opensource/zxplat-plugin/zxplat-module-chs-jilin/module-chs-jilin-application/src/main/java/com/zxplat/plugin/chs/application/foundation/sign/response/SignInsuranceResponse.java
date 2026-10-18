/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.foundation.sign.response;

import lombok.Data;
import lombok.experimental.Accessors;

/** 医保签到出参 */
@Data
@Accessors(chain = true)
public class SignInsuranceResponse {

    /** 签到流水号 */
    private String signNo;
}
