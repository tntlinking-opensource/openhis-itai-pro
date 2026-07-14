/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.bridge.push.access;

import com.openhis.whale.crb.domain.push.access.CrbFrontSoftwareSsoAccess;
import com.openhis.whale.crb.gateway.client.NationalFrontSoftwareClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 前置软件 SSO 端口 bridge 适配器（P10-A）。
 *
 * <p><b>职责：</b>将 domain {@link CrbFrontSoftwareSsoAccess} 委托至 {@link NationalFrontSoftwareClient} 完成 HTTP 出站。</p>
 * <p><b>实现端口：</b>{@link com.openhis.whale.crb.domain.push.access.CrbFrontSoftwareSsoAccess}</p>
 * <p><b>调用方：</b>{@code InfectiousIntegrationAppService}</p>
 * <p><b>架构边界：</b>bridge 层组装 gateway 出站上下文；application 只依赖 domain 端口。</p>
 * <p><b>迁移阶段：</b>P10-A</p>
 */

@Component
@RequiredArgsConstructor
public class CrbFrontSoftwareSsoAccessAdapter implements CrbFrontSoftwareSsoAccess {

    private final NationalFrontSoftwareClient nationalFrontSoftwareClient;

    @Override
    public String obtainSsoToken(String userId) {
        return nationalFrontSoftwareClient.obtainSsoToken(userId);
    }
}
