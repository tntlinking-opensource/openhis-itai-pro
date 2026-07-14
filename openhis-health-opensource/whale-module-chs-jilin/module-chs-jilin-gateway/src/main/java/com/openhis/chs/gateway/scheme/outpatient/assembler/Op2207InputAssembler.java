/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.outpatient.assembler;

import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;
import com.openhis.chs.gateway.scheme.outpatient.model.Op2207Input;

import com.openhis.chs.domain.outpatient.command.OpPreSettleCommand;
import com.openhis.chs.gateway.scheme.common.model.ChsSetlinfoOutput;
import com.openhis.chs.gateway.scheme.common.model.ChsSettleData;
import com.openhis.chs.gateway.scheme.outpatient.model.Op2207Input;

import java.math.BigDecimal;
import com.openhis.chs.gateway.scheme.outpatient.model.Op2207Input;

/** 【2207】门诊费用结算报文组装（2206 setlinfo 金额透传） */
public final class Op2207InputAssembler {

    private Op2207InputAssembler() {
    }

    public static Op2207Input assemble(
            OpPreSettleCommand ctx,
            String mdtrtId,
            String chrgBchno,
            BigDecimal medfeeSumamt,
            ChsSetlinfoOutput setlinfo) {
        ChsSettleData data = OpOutpatientSettleInputAssembler.baseSettleData(ctx, mdtrtId, chrgBchno, medfeeSumamt);
        if (setlinfo != null) {
            data.setFulamtOwnpayAmt(setlinfo.getFulamtOwnpayAmt());
            data.setOverlmtSelfpay(setlinfo.getOverlmtSelfpay());
            data.setPreselfpayAmt(setlinfo.getPreselfpayAmt());
            data.setInscpScpAmt(setlinfo.getInscpScpAmt());
        }
        if (isNotEmpty(ctx.getInvono())) {
            data.setInvono(ctx.getInvono().trim());
        }
        data.setMinpacuntDrugTracCnt(OpOutpatientSettleInputAssembler.resolveTraceCount(ctx.getMinpacuntDrugTracCnt()));
        data.setMcsTracCnt(OpOutpatientSettleInputAssembler.resolveTraceCount(ctx.getMcsTracCnt()));
        Op2207Input input = new Op2207Input();
        input.setData(data);
        return input;
    }
}
