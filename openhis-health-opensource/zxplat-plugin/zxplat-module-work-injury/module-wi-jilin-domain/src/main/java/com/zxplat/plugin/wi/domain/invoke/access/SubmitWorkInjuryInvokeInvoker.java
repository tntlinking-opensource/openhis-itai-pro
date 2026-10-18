/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.domain.invoke.access;

import com.zxplat.plugin.wi.domain.invoke.command.SubmitWorkInjuryInvokeResultRequest;
import com.zxplat.plugin.wi.domain.invoke.result.SubmitWorkInjuryInvokeResultResponse;

/**
 * Win invoke submit 出站端口。
 *
 * <p><b>实现位置：</b>{@code com.zxplat.plugin.wi.bridge.invoke.invoker.SubmitWorkInjuryInvokeInvokerImpl}</p>
 * <p><b>调用方：</b>{@code WorkInjuryInvokeAppService}、{@code WorkInjuryInvokeExtensionImpl}</p>
 */
public interface SubmitWorkInjuryInvokeInvoker {

    /**
     * 提交 Win 回写结果，写审计并推进 invoke 链。
     *
     * @param request submit 请求
     * @return submit 响应（含 nextAction、summary）
     */
    SubmitWorkInjuryInvokeResultResponse invoke(SubmitWorkInjuryInvokeResultRequest request);
}
