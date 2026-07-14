/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.outpatient.parser;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import com.openhis.chs.gateway.scheme.outpatient.model.Op2201Output;
import com.openhis.chs.gateway.scheme.outpatient.model.Op2201OutputData;

import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.common.model.ChsSchemeJsonSupport;
import com.openhis.chs.gateway.support.ChsPlatformResponseSupport;
import com.openhis.whale.core.application.exception.BusinessException;

/** 【2201】output 解析 */
public final class Op2201OutputParser {

    private Op2201OutputParser() {
    }

    public static Op2201Output requireResult(ResultBody body) {
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.OUTPATIENT_REG);
        Op2201OutputData fromData = ChsSchemeJsonSupport.parseNested(body, "data", Op2201OutputData.class);
        if (fromData != null && !isEmpty(fromData.getMdtrtId())) {
            Op2201Output wrapped = new Op2201Output();
            wrapped.setData(fromData);
            return wrapped;
        }
        Op2201Output root = ChsSchemeJsonSupport.parseOutput(body, Op2201Output.class);
        if (root != null) {
            String mdtrtId = resolveMdtrtId(root);
            if (!isEmpty(mdtrtId)) {
                return root;
            }
        }
        throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "2201 未返回 mdtrt_id，请先完成医保挂号登记");
    }

    public static String resolveMdtrtId(Op2201Output output) {
        if (output == null) {
            return null;
        }
        if (output.getData() != null && !isEmpty(output.getData().getMdtrtId())) {
            return output.getData().getMdtrtId().trim();
        }
        if (!isEmpty(output.getMdtrtId())) {
            return output.getMdtrtId().trim();
        }
        return null;
    }
}
