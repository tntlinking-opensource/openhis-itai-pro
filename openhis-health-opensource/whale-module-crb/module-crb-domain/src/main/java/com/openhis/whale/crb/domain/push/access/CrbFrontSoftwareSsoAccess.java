/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.push.access;

/**
 * 前置软件 SSO token 获取出站端口（P10-A）。
 *
 * <p><b>职责：</b>定义传染病前置软件单点登录 token 的 domain 出站契约，屏蔽 HTTP 与 gateway 细节。</p>
 * <p><b>实现位置：</b>{@code com.openhis.whale.crb.bridge.push.access.CrbFrontSoftwareSsoAccessAdapter}</p>
 * <p><b>调用方：</b>{@code InfectiousIntegrationAppService}（P1 联调）</p>
 * <p><b>架构边界：</b>application 经本端口调用，禁止直接注入 {@code com.openhis.whale.crb.gateway.client.*}。</p>
 * <p><b>迁移阶段：</b>P10-A</p>
 */

public interface CrbFrontSoftwareSsoAccess {

    /**
     * 获取前置软件 SSO token 或跳转 URL 片段。
     *
     * @param userId 已推送 {@code base_user} 的用户 id
     * @return 平台返回的 token 或 URL 片段
     */
    String obtainSsoToken(String userId);
}
