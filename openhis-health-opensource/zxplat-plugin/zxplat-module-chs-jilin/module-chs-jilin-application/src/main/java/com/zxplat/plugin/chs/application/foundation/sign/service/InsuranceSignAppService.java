/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.foundation.sign.service;

import com.zxplat.plugin.chs.application.foundation.sign.mapper.InsuranceSignAppMapper;
import com.zxplat.plugin.chs.application.foundation.sign.request.SignInsuranceRequest;
import com.zxplat.plugin.chs.application.foundation.sign.response.SignInsuranceResponse;
import com.zxplat.plugin.chs.domain.session.access.InsuranceSignInAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 医保签到 UI 应用服务（POST /api/app/insuranceSign/*）。
 * <p>职责：调用网关签到并返回签到流水号 signNo。</p>
 */
@Service
@RequiredArgsConstructor
public class InsuranceSignAppService {

    private final InsuranceSignInAccess signInAccess;

    /**
     * 执行医保签到并返回签到流水号。
     */
    public SignInsuranceResponse signIn(SignInsuranceRequest request) {
        SignInsuranceRequest safe = request == null ? new SignInsuranceRequest() : request;
        if (safe.getContractBusNo() == null || safe.getContractBusNo().isBlank()) {
            throw new IllegalArgumentException("contractBusNo 不能为空");
        }
        String signNo = signInAccess.signIn(InsuranceSignAppMapper.toSignInCommand(safe));
        return new SignInsuranceResponse().setSignNo(signNo);
    }
}
