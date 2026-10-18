/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.outpatient.assembler;

import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import com.zxplat.plugin.chs.domain.outpatient.command.OpPreSettleCommand;
import com.zxplat.plugin.chs.domain.outpatient.command.OpSettleChargeLine;
import com.zxplat.plugin.chs.domain.share.constants.ChsInventoryConstants;
import com.zxplat.plugin.chs.gateway.scheme.common.model.ChsFeeDetailLine;
import com.zxplat.plugin.chs.gateway.scheme.outpatient.model.Op2204Input;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

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
                .setLocalItemName(line.getItemName())
                .setLocalCatalogKind(line.getCatalogKind())
                .setDetItemFeeSumamt(scalePlatformAmt(line.getDetItemFeeSumamt()))
                .setCnt(line.getCnt())
                .setPric(scalePlatformPric(line.getPric()))
                .setBilgDeptCodg(OpOutpatientSettleInputAssembler.blankToDash(line.getBilgDeptCodg()))
                .setBilgDeptName(OpOutpatientSettleInputAssembler.blankToDash(line.getBilgDeptName()))
                .setBilgDrCodg(OpOutpatientSettleInputAssembler.blankToDash(line.getBilgDrCodg()))
                .setBilgDrName(OpOutpatientSettleInputAssembler.blankToDash(line.getBilgDrName()))
                .setHospApprFlag("1");
            // Pre：退单行带原费用流水（与 2301 同口径）
            if (isNotEmpty(line.getInitFeedetlSn())) {
                row.setInitFeedetlSn(line.getInitFeedetlSn().trim());
            }
            if (isNotEmpty(line.getDiseCodg())) {
                row.setDiseCodg(line.getDiseCodg());
            }
            if (isNotEmpty(line.getRxno())) {
                row.setRxno(line.getRxno());
            }
            // Pre：中草药按处方味数传复方/单方；非中草药不传
            if (isNotEmpty(line.getTcmdrugUsedWay())) {
                row.setTcmdrugUsedWay(line.getTcmdrugUsedWay().trim());
            }
            feedetail.add(row);
        }
        Op2204Input input = new Op2204Input();
        input.setFeedetail(feedetail);
        input.setInsuplcAdmdvs(ctx.getInsuplcAdmdvs());
        return input;
    }

    /** 2204 {@code pric}：最多 6 位 HALF_UP（规范 16,6；禁止压成 2 位破坏平摊）。 */
    private static BigDecimal scalePlatformPric(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(ChsInventoryConstants.UPLOAD_PRICE_SCALE, RoundingMode.HALF_UP);
    }

    /** 2204 {@code det_item_fee_sumamt}：2 位 HALF_UP（规范 16,2）。 */
    private static BigDecimal scalePlatformAmt(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}

