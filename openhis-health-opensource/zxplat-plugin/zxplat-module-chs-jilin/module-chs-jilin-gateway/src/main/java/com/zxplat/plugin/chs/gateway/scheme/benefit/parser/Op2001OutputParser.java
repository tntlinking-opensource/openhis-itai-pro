/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.benefit.parser;

import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.gateway.scheme.common.model.ChsSchemeJsonSupport;
import com.zxplat.plugin.chs.gateway.support.ChsPlatformResponseSupport;
import com.zxplat.plugin.chs.gateway.scheme.benefit.model.Op2001Output;

/** 【2001】output 解析与校验 */
public final class Op2001OutputParser {

    private Op2001OutputParser() {
    }

    public static Op2001Output requireOutput(ResultBody body) {
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.PERSON_TREATMENT_CHECK);
        Op2001Output output = ChsSchemeJsonSupport.parseOutput(body, Op2001Output.class);
        if (output == null) {
            return new Op2001Output();
        }
        return output;
    }
}
