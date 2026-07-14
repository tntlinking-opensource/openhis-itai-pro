/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.outpatient.assembler;

import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;
import com.openhis.chs.gateway.scheme.outpatient.model.Op2204Input;

import com.openhis.chs.domain.outpatient.command.OpPreSettleCommand;
import com.openhis.chs.domain.outpatient.command.OpSettleChargeLine;
import com.openhis.chs.gateway.scheme.common.model.ChsFeeDetailLine;
import com.openhis.chs.gateway.scheme.outpatient.model.Op2204Input;

import java.util.ArrayList;
import java.util.List;
import com.openhis.chs.gateway.scheme.outpatient.model.Op2204Input;

/** 【2204】费用明细上传报文组装 */
public final class Op2204InputAssembler {

    private Op2204InputAssembler() {
    }

    public static Op2204Input assemble(OpPreSettleCommand ctx, String mdtrtId, String chrgBchno) {
        List<ChsFeeDetailLine> feedetail = new ArrayList<>();
        for (OpSettleChargeLine line : ctx.getChargeLines()) {
            ChsFeeDetailLine row = new ChsFeeDetailLine()
                .setFeedetlSn(line.getFeedetlSn())
                .setMdtrtId(mdtrtId)
                .setPsnNo(ctx.getPsnNo())
                .setChrgBchno(chrgBchno)
                .setRxCircFlag("0")
                .setFeeOcurTime(line.getFeeOcurTime().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .setMedListCodg(line.getMedListCodg())
                .setMedinsListCodg(line.getMedinsListCodg())
                .setDetItemFeeSumamt(line.getDetItemFeeSumamt())
                .setCnt(line.getCnt())
                .setPric(line.getPric())
                .setBilgDeptCodg(OpOutpatientSettleInputAssembler.blankToDash(line.getBilgDeptCodg()))
                .setBilgDeptName(OpOutpatientSettleInputAssembler.blankToDash(line.getBilgDeptName()))
                .setBilgDrCodg(OpOutpatientSettleInputAssembler.blankToDash(line.getBilgDrCodg()))
                .setBilgDrName(OpOutpatientSettleInputAssembler.blankToDash(line.getBilgDrName()))
                .setHospApprFlag("1");
            if (isNotEmpty(line.getDiseCodg())) {
                row.setDiseCodg(line.getDiseCodg());
            }
            if (isNotEmpty(line.getRxno())) {
                row.setRxno(line.getRxno());
            }
            feedetail.add(row);
        }
        Op2204Input input = new Op2204Input();
        input.setFeedetail(feedetail);
        input.setInsuplcAdmdvs(ctx.getInsuplcAdmdvs());
        return input;
    }
}
