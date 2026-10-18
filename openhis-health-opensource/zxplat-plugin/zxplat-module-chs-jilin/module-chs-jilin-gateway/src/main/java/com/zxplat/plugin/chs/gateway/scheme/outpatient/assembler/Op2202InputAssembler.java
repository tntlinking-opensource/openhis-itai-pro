/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.outpatient.assembler;

import com.zxplat.plugin.chs.gateway.scheme.outpatient.model.Op2202Input;

/** 【2202】门诊挂号撤销报文组装 */
public final class Op2202InputAssembler {

    private Op2202InputAssembler() {
    }

    public static Op2202Input assemble(String psnNo, String mdtrtId, String iptOtpNo) {
        Op2202Input.Op2202Data data = new Op2202Input.Op2202Data()
            .setPsnNo(psnNo)
            .setMdtrtId(mdtrtId)
            .setIptOtpNo(iptOtpNo);
        Op2202Input input = new Op2202Input();
        input.setData(data);
        return input;
    }
}
