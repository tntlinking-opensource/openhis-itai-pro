/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.benefit.assembler;

import com.openhis.chs.domain.share.enums.MedType;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;
import com.openhis.chs.gateway.scheme.benefit.model.Op2001Input;

/** 【2001】input 组装 */
public final class Op2001InputAssembler {

    private Op2001InputAssembler() {
    }

    public static Op2001Input assemble(
            String psnNo,
            String insutype,
            String fixmedinsCode,
            String medType,
            String begntime) {
        String resolvedMedType = MedType.GENERAL_OUTPATIENT.getCode();
        if (isNotEmpty(medType)) {
            resolvedMedType = medType.trim();
        }
        Op2001Input.Op2001Data data = new Op2001Input.Op2001Data()
            .setPsnNo(psnNo)
            .setInsutype(insutype)
            .setFixmedinsCode(fixmedinsCode)
            .setMedType(resolvedMedType)
            .setBegntime(begntime);
        Op2001Input input = new Op2001Input();
        input.setData(data);
        return input;
    }
}
