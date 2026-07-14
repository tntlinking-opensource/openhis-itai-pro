/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.foundation.session.service;

import com.openhis.chs.application.foundation.session.mapper.InsuranceSessionAppMapper;
import com.openhis.chs.application.foundation.session.request.InsuranceSessionSignInRequest;
import com.openhis.chs.application.foundation.sign.service.InsuranceSignAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 医保会话兼容入口（{@code POST /api/app/insuranceSession/signIn}）。
 * <p>新路由为 {@code /api/app/insuranceSign/signIn}，本类委托 {@link InsuranceSignAppService} 完成签到。</p>
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InsuranceSessionAppService {

    private final InsuranceSignAppService insuranceSignAppService;

    /**
     * 兼容旧路由的医保签到，返回签到流水号。
     */
    public String signIn(InsuranceSessionSignInRequest request) {
        return insuranceSignAppService.signIn(InsuranceSessionAppMapper.toSignRequest(request)).getSignNo();
    }
}
