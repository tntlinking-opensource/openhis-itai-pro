/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.settlement.assembler;

import com.openhis.chs.gateway.scheme.settlement.model.Op2601Data;
import com.openhis.chs.gateway.scheme.settlement.model.Op2601Input;

/** 【2601】冲正报文组装 */
public final class Op2601InputAssembler {

    private Op2601InputAssembler() {
    }

    public static Op2601Input assemble(String psnNo, String omsgid, String oinfno) {
        Op2601Data data = new Op2601Data()
            .setPsnNo(psnNo)
            .setOmsgid(omsgid)
            .setOinfno(oinfno);
        Op2601Input input = new Op2601Input();
        input.setData(data);
        return input;
    }
}
