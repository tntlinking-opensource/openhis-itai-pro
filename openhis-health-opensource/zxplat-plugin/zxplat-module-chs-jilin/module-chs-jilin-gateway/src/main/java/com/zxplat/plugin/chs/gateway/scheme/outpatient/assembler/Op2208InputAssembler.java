/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.outpatient.assembler;

import com.zxplat.plugin.chs.gateway.scheme.common.model.ChsRevokeData;
import com.zxplat.plugin.chs.gateway.scheme.outpatient.model.Op2208Input;

/** 【2208】撤销门诊正式结算报文组装 */
public final class Op2208InputAssembler {

    private Op2208InputAssembler() {
    }

    public static Op2208Input assemble(String mdtrtId, String setlId, String psnNo) {
        ChsRevokeData data = new ChsRevokeData()
            .setMdtrtId(mdtrtId)
            .setSetlId(setlId)
            .setPsnNo(psnNo);
        Op2208Input input = new Op2208Input();
        input.setData(data);
        return input;
    }
}
