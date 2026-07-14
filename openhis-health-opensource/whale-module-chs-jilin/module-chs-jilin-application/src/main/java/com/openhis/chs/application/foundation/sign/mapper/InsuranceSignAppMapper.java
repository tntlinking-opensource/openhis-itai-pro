/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.foundation.sign.mapper;

import com.openhis.chs.application.foundation.sign.request.SignInsuranceRequest;
import com.openhis.chs.domain.session.command.SignInCommand;

/**
 * 医保签到：UI 入参与 domain 命令映射。
 */
public final class InsuranceSignAppMapper {

    private InsuranceSignAppMapper() {
    }

    /** 将签到入参转为 domain 签到命令。 */
    public static SignInCommand toSignInCommand(SignInsuranceRequest request) {
        SignInsuranceRequest safe = request == null ? new SignInsuranceRequest() : request;
        return new SignInCommand()
            .setContractBusNo(safe.getContractBusNo())
            .setOperatorId(safe.getOperatorId())
            .setOperatorName(safe.getOperatorName())
            .setSignMac(safe.getSignMac())
            .setSignIp(safe.getSignIp());
    }
}
