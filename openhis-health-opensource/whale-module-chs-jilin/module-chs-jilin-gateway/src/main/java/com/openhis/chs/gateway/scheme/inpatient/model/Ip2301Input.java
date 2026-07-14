/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.inpatient.model;

import com.openhis.chs.gateway.scheme.common.model.ChsFeeDetailLine;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** 【2301】住院费用明细上传 input（feedetail） */
@Data
@Accessors(chain = true)
public class Ip2301Input {

    private List<ChsFeeDetailLine> feedetail;
}
