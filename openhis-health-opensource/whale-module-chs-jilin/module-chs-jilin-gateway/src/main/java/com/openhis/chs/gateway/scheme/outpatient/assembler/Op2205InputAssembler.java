/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.outpatient.assembler;

import com.openhis.chs.gateway.scheme.common.model.ChsRevokeData;
import com.openhis.chs.gateway.scheme.outpatient.model.Op2205Input;

/** 【2205】撤销未结算费用明细报文组装 */
public final class Op2205InputAssembler {

    private Op2205InputAssembler() {
    }

    public static Op2205Input assemble(String mdtrtId, String chrgBchno, String psnNo) {
        ChsRevokeData data = new ChsRevokeData()
            .setMdtrtId(mdtrtId)
            .setChrgBchno(chrgBchno)
            .setPsnNo(psnNo);
        Op2205Input input = new Op2205Input();
        input.setData(data);
        return input;
    }
}
