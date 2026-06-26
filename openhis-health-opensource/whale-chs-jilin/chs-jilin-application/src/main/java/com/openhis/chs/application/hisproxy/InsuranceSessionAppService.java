/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.hisproxy;

import com.openhis.chs.domain.catalog.dto.request.SignInRequest;
import com.openhis.chs.domain.catalog.facade.ChsSessionOperations;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 医保会话 bridge 代理（方案 1A：前端仍调 {@code /api/app/insuranceSession/signIn}）。
 *
 * <p>embedded：委托同 JVM {@link ChsSessionOperations}；remote：委托 HTTP 至 chs-jilin-application。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InsuranceSessionAppService {

    private final ChsSessionOperations sessionOperations;

    /**
     * 【9001】签到。
     *
     * @param request 契约 bus_no 与可选 MAC/IP
     * @return 平台 sign_no
     */
    public String signIn(SignInRequest request) {
        // Cmd：委托 facade
        return sessionOperations.signIn(request);
    }
}
