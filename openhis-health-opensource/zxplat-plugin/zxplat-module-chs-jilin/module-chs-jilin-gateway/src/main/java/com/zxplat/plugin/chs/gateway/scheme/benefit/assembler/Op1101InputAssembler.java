/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.benefit.assembler;

import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import com.zxplat.plugin.chs.domain.share.enums.MdtrtCertType;
import com.zxplat.plugin.chs.domain.share.enums.PsnCertType;
import com.zxplat.plugin.chs.gateway.scheme.benefit.model.Op1101Input;
import com.zxplat.plugin.chs.gateway.scheme.common.model.ChsMdtrtCertInput;

/** 【1101】人员信息获取报文组装 */
public final class Op1101InputAssembler {

    private Op1101InputAssembler() {
    }

    /** 1101 input.data（电子凭证 01 时 psnName 在吉林平台必填） */
    public static Op1101Input assemble(
            String mdtrtCertType,
            String mdtrtCertNo,
            String cardSn,
            String psnCertType,
            String certno,
            String psnName) {
        ChsMdtrtCertInput data = new ChsMdtrtCertInput()
            .setMdtrtCertType(mdtrtCertType)
            .setMdtrtCertNo(mdtrtCertNo);
        if (MdtrtCertType.SOCIAL_SECURITY_CARD.getCode().equals(mdtrtCertType)) {
            String cardSnValue = "";
            if (cardSn != null) {
                cardSnValue = cardSn.trim();
            }
            data.setCardSn(cardSnValue);
            if (isNotEmpty(certno)) {
                data.setCertno(certno.trim());
            }
            data.setPsnCertType(resolvePsnCertType(psnCertType));
        } else if (MdtrtCertType.ID_CARD.getCode().equals(mdtrtCertType)) {
            if (isNotEmpty(certno)) {
                data.setCertno(certno.trim());
            }
            data.setPsnCertType(resolvePsnCertType(psnCertType));
        } else if (MdtrtCertType.ELECTRONIC.getCode().equals(mdtrtCertType)) {
            data.setPsnCertType(resolvePsnCertType(psnCertType));
            if (isNotEmpty(certno)) {
                data.setCertno(certno.trim());
            }
            if (isNotEmpty(psnName)) {
                data.setPsnName(psnName.trim());
            }
        }
        Op1101Input input = new Op1101Input();
        input.setData(data);
        return input;
    }

    private static String resolvePsnCertType(String psnCertType) {
        if (isNotEmpty(psnCertType)) {
            return psnCertType.trim();
        }
        return PsnCertType.ID_CARD.getCode();
    }
}
