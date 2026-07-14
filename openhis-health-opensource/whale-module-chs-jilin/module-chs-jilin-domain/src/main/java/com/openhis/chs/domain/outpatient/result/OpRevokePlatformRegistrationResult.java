/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.outpatient.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2202 门诊挂号撤销结果 */
@Data
@Accessors(chain = true)
public class OpRevokePlatformRegistrationResult {

    private String mdtrtId;
    /** true 表示已调平台 2202；false 表示本地无有效映射跳过 */
    private boolean revoked;
}
