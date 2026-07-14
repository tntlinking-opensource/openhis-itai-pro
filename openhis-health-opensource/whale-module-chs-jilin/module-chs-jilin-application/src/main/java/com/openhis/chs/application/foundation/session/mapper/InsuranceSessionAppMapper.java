/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.foundation.session.mapper;

import com.openhis.chs.application.foundation.session.request.InsuranceSessionSignInRequest;
import com.openhis.chs.application.foundation.sign.request.SignInsuranceRequest;

/**
 * 医保会话兼容入口：旧签到入参与新 {@link SignInsuranceRequest} 映射。
 */
public final class InsuranceSessionAppMapper {

    private InsuranceSessionAppMapper() {
    }

    /** 将会话签到入参转为标准签到入参。 */
    public static SignInsuranceRequest toSignRequest(InsuranceSessionSignInRequest request) {
        InsuranceSessionSignInRequest safe = request == null ? new InsuranceSessionSignInRequest() : request;
        return new SignInsuranceRequest()
            .setContractBusNo(safe.getContractBusNo())
            .setSignMac(safe.getMac())
            .setSignIp(safe.getIp());
    }
}
