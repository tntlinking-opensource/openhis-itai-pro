/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.outpatient.assembler;

import com.openhis.chs.domain.outpatient.command.OpPreSettleCommand;
import com.openhis.chs.gateway.scheme.common.model.ChsDiseInfoRow;
import com.openhis.chs.gateway.scheme.outpatient.model.Op2203Input;
import com.openhis.chs.gateway.scheme.outpatient.model.Op2203MdtrtInfo;

import java.util.List;
import com.openhis.chs.gateway.scheme.outpatient.model.Op2203Input;
import com.openhis.chs.gateway.scheme.outpatient.model.Op2203MdtrtInfo;

/** 【2203】就诊信息上传报文组装 */
public final class Op2203InputAssembler {

    private Op2203InputAssembler() {
    }

    public static Op2203Input assemble(OpPreSettleCommand ctx, String mdtrtId) {
        Op2203MdtrtInfo mdtrtinfo = new Op2203MdtrtInfo()
            .setMdtrtId(mdtrtId)
            .setPsnNo(ctx.getPsnNo())
            .setMedType(OpOutpatientSettleInputAssembler.resolveMedTypeForContext(ctx))
            .setBegntime(OpOutpatientSettleInputAssembler.formatTime(ctx.getEncounterStartTime()))
            .setMainCondDscr(OpOutpatientSettleInputAssembler.blankToDash(ctx.getMainDiagName()));
        ChsDiseInfoRow dise = new ChsDiseInfoRow()
            .setDiagType("1")
            .setDiagSrtNo("1")
            .setDiagCode(OpOutpatientSettleInputAssembler.blankToDash(ctx.getMainDiagCode()))
            .setDiagName(OpOutpatientSettleInputAssembler.blankToDash(ctx.getMainDiagName()))
            .setDiagDept(OpOutpatientSettleInputAssembler.blankToDash(ctx.getDeptCode()))
            .setDiseDorNo(OpOutpatientSettleInputAssembler.blankToDash(ctx.getAtddrNo()))
            .setDiseDorName(OpOutpatientSettleInputAssembler.blankToDash(ctx.getDrName()))
            .setDiagTime(OpOutpatientSettleInputAssembler.formatTime(ctx.getEncounterStartTime()));
        return new Op2203Input()
            .setMdtrtinfo(mdtrtinfo)
            .setDiseinfo(List.of(dise));
    }
}
