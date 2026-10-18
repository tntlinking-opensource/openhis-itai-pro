/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.domain.invoke.access;

import com.zxplat.plugin.wi.domain.invoke.command.PrepareWorkInjuryInvokeRequest;
import com.zxplat.plugin.wi.domain.invoke.result.PrepareWorkInjuryInvokeResponse;

/**
 * Win invoke prepare 出站端口。
 *
 * <p><b>实现位置：</b>{@code com.zxplat.plugin.wi.bridge.invoke.invoker.PrepareWorkInjuryInvokeInvokerImpl}</p>
 * <p><b>调用方：</b>{@code WorkInjuryInvokeAppService}、{@code WorkInjuryInvokeExtensionImpl}</p>
 */
public interface PrepareWorkInjuryInvokeInvoker {

    /**
     * 创建/推进 invoke 会话并下发本步 Win input。
     *
     * @param request prepare 请求
     * @return prepare 响应（含 sessionId、input、nextAction）
     */
    PrepareWorkInjuryInvokeResponse invoke(PrepareWorkInjuryInvokeRequest request);
}
