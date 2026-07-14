/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.outpatient.assembler;

import com.openhis.chs.domain.outpatient.command.OpPreSettleCommand;
import com.openhis.chs.gateway.scheme.common.model.ChsSettleData;
import com.openhis.chs.gateway.scheme.outpatient.model.Op2206Input;

import java.math.BigDecimal;
import com.openhis.chs.gateway.scheme.outpatient.model.Op2206Input;

/** 【5206】门诊费用预结算报文组装 */
public final class Op2206InputAssembler {

    private Op2206InputAssembler() {
    }

    public static Op2206Input assemble(
            OpPreSettleCommand ctx,
            String mdtrtId,
            String chrgBchno,
            BigDecimal medfeeSumamt) {
        ChsSettleData data = OpOutpatientSettleInputAssembler.baseSettleData(ctx, mdtrtId, chrgBchno, medfeeSumamt);
        Op2206Input input = new Op2206Input();
        input.setData(data);
        return input;
    }
}
