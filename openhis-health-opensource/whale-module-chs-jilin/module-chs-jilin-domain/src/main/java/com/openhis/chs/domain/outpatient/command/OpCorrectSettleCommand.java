/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.outpatient.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 【2601】冲正触发（ExtensionImpl → Op2601Invoker 补全 omsgid 等） */
@Data
@Accessors(chain = true)
public class OpCorrectSettleCommand {

    private Long settlementRecordId;
    private String originalInfno;
}
