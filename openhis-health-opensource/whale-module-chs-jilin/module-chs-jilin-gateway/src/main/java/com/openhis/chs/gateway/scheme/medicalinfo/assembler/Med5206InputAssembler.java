/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.medicalinfo.assembler;

import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;
import com.openhis.chs.gateway.scheme.medicalinfo.model.Med5206Input;

/** 【5206】input 组装 */
public final class Med5206InputAssembler {

    private Med5206InputAssembler() {
    }

    public static Med5206Input assemble(String psnNo, String cumYm) {
        Med5206Input.Med5206Data data = new Med5206Input.Med5206Data().setPsnNo(psnNo);
        if (isNotEmpty(cumYm)) {
            data.setCumYm(cumYm.trim());
        }
        Med5206Input input = new Med5206Input();
        input.setData(data);
        return input;
    }
}
