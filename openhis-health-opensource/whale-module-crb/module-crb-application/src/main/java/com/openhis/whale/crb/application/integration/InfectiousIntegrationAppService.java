/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.application.integration;

import com.openhis.whale.crb.domain.config.CrbProperties;
import com.openhis.whale.crb.domain.push.access.CrbFrontSoftwareSsoAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/**
 * 前置 SSO 与连通性 AppService（POST /api/app/infectiousIntegration/{methodName}）。
 */
@Service
@Validated
@RequiredArgsConstructor
public class InfectiousIntegrationAppService {

    private final CrbFrontSoftwareSsoAccess crbFrontSoftwareSsoAccess;
    private final CrbProperties crbProperties;

    /**
     * 构建前置软件 SSO 跳转 token（P1 联调）。
     *
     * @param userId 已推送 base_user 的用户 id 字符串
     * @return token；Mock 模式下返回占位串
     */
    public String getFrontSoftwareSsoUrl(String userId) {
        // Gate：无用户则无法 SSO 到前置待办
        if (userId == null || userId.isBlank()) {
            return "";
        }
        // Pre：读取院端 license 配置（骨架阶段仅校验非空）
        String license = crbProperties.getFrontSoftware().getLicense();
        if (license == null || license.isBlank()) {
            return "";
        }
        // Cmd：委托前置 Client 换取 token
        return crbFrontSoftwareSsoAccess.obtainSsoToken(userId);
    }
}
