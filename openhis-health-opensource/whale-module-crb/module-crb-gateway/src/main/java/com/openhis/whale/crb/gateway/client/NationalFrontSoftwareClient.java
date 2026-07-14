/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.gateway.client;

import com.openhis.whale.crb.domain.push.result.PushResult;

/**
 * 国家传染病前置软件 receive HTTP 客户端。
 */
public interface NationalFrontSoftwareClient {

    /**
     * POST upsert 指定 resource 的 JSON body。
     *
     * @param resource receive 路径段
     * @param payloadJson UTF-8 JSON 字符串
     * @return 前置响应摘要
     */
    PushResult upsert(String resource, String payloadJson);

    /**
     * DELETE 指定 resource 下的记录。
     *
     * @param resource receive 路径段
     * @param id 前置侧业务 id
     * @return 前置响应摘要
     */
    PushResult delete(String resource, String id);

    /**
     * 获取 SSO token（P1 联调使用）。
     *
     * @param userId 已推送 base_user 的用户 id
     * @return token 或跳转 URL 片段
     */
    String obtainSsoToken(String userId);
}
