/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.medicalinfo.assembler;

import com.openhis.chs.gateway.scheme.medicalinfo.model.Med5301Input;

/** 【5301】input 组装 */
public final class Med5301InputAssembler {

    private Med5301InputAssembler() {
    }

    public static Med5301Input assemble(String psnNo) {
        Med5301Input.Med5301Data data = new Med5301Input.Med5301Data().setPsnNo(psnNo);
        Med5301Input input = new Med5301Input();
        input.setData(data);
        return input;
    }
}
