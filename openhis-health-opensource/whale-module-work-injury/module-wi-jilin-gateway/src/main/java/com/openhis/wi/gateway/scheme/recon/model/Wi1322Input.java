/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.gateway.scheme.recon.model;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 【1322】工伤对账确认入参（10-P0 §11）。
 */
@Data
@Accessors(chain = true)
public class Wi1322Input {

    /** 对账月份 yyyyMM。 */
    private String month;

    /** 统筹区编码。 */
    private String socialPoolCode;
}
