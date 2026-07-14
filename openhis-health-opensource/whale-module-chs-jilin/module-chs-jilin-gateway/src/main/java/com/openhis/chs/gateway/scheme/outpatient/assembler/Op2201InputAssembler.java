/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.outpatient.assembler;

import com.openhis.chs.domain.outpatient.command.OpPreSettleCommand;
import com.openhis.chs.gateway.scheme.outpatient.model.Op2201Input;
import com.openhis.chs.gateway.scheme.outpatient.model.Op2201RegData;

/** 【2201】门诊挂号报文组装 */
public final class Op2201InputAssembler {

    private Op2201InputAssembler() {
    }

    public static Op2201Input assemble(OpPreSettleCommand ctx) {
        Op2201RegData data = new Op2201RegData()
            .setPsnNo(ctx.getPsnNo())
            .setInsutype(ctx.getInsutype())
            .setBegntime(OpOutpatientSettleInputAssembler.formatTime(ctx.getEncounterStartTime()))
            .setMdtrtCertType(ctx.getMdtrtCertType())
            .setMdtrtCertNo(ctx.getMdtrtCertNo())
            .setIptOtpNo(ctx.getEncounterBusNo())
            .setAtddrNo(OpOutpatientSettleInputAssembler.blankToDash(ctx.getAtddrNo()))
            .setDrName(OpOutpatientSettleInputAssembler.blankToDash(ctx.getDrName()))
            .setDeptCode(OpOutpatientSettleInputAssembler.blankToDash(ctx.getDeptCode()))
            .setDeptName(OpOutpatientSettleInputAssembler.blankToDash(ctx.getDeptName()))
            .setCaty(OpOutpatientSettleInputAssembler.blankToDash(ctx.getCaty()));
        Op2201Input input = new Op2201Input();
        input.setData(data);
        return input;
    }
}
