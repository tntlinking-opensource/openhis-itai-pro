/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.inpatient.parser;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import com.openhis.chs.gateway.scheme.inpatient.model.Ip2401Output;

import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.common.model.ChsSchemeJsonSupport;
import com.openhis.chs.gateway.support.ChsPlatformResponseSupport;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.chs.gateway.scheme.inpatient.model.Ip2401Output;

/** 【2401】output.result.mdtrt_id 解析 */
public final class Ip2401OutputParser {

    private Ip2401OutputParser() {
    }

    public static Ip2401Output requireResult(ResultBody body) {
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.INPATIENT_ADMISSION);
        Ip2401Output fromResult = ChsSchemeJsonSupport.parseNested(body, "result", Ip2401Output.class);
        if (fromResult != null && !isEmpty(fromResult.getMdtrtId())) {
            return fromResult;
        }
        Ip2401Output root = ChsSchemeJsonSupport.parseOutput(body, Ip2401Output.class);
        if (root != null && !isEmpty(root.getMdtrtId())) {
            return root;
        }
        throw new BusinessException("INS-IP-009", "2401 未返回 mdtrt_id，请先完成医保入院登记");
    }

    public static String resolveMdtrtId(Ip2401Output output) {
        if (output == null || isEmpty(output.getMdtrtId())) {
            return null;
        }
        return output.getMdtrtId().trim();
    }
}
