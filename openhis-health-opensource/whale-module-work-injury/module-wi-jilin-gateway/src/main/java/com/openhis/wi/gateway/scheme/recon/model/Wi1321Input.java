/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.gateway.scheme.recon.model;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 【1321】工伤明细对账入参（10-P0 §11）。
 */
@Data
@Accessors(chain = true)
public class Wi1321Input {

    /** 对账月份 yyyyMM。 */
    private String month;

    /** 统筹区编码。 */
    private String socialPoolCode;
}
