/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.benefit.mapper;

import com.openhis.chs.domain.outpatient.result.YbTreatmentCheckResult;
import com.openhis.chs.domain.outpatient.command.YbTreatmentInfoRow;
import com.openhis.chs.gateway.scheme.benefit.model.Op2001Output;
import com.openhis.chs.gateway.scheme.benefit.model.Op2001TrtinfoRow;

/** Op2001Output → {@link YbTreatmentCheckResult} 静态映射（无 IO、无 Spring 依赖）。 */
public final class Op2001ResultMapper {

    private Op2001ResultMapper() {
    }

    /** 将 2001 平台输出 trtinfo 列表映射为 domain 待遇检查结果。 */
    public static YbTreatmentCheckResult fromOutput(Op2001Output output) {
        YbTreatmentCheckResult result = new YbTreatmentCheckResult();
        if (output == null || output.getTrtinfo() == null) {
            return result;
        }
        for (Op2001TrtinfoRow item : output.getTrtinfo()) {
            if (item == null) {
                continue;
            }
            YbTreatmentInfoRow row = new YbTreatmentInfoRow();
            row.setPsnNo(item.getPsnNo());
            row.setTrtChkType(item.getTrtChkType());
            row.setFundPayType(item.getFundPayType());
            row.setTrtEnjymntFlag(item.getTrtEnjymntFlag());
            row.setBegndate(item.getBegndate());
            row.setEnddate(item.getEnddate());
            row.setTrtChkRslt(item.getTrtChkRslt());
            result.getTrtinfoList().add(row);
        }
        return result;
    }
}
