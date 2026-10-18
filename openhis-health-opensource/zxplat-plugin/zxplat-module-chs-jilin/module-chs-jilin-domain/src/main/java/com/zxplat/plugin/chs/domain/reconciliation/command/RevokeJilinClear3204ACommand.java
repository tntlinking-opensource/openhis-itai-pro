/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.reconciliation.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 【3204A】吉林清算撤销 */
@Data
@Accessors(chain = true)
public class RevokeJilinClear3204ACommand {

    private String contractBusNo;
    private Long contractId;
    private String clrAppyEvtId;
    private String clrOptins;
}
