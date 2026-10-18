/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.domain.invoke.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

/**
 * prepareInvoke 响应（前端 → Zxplat.Win invoke 循环）。
 *
 * <p>{@code nextAction=INVOKE_WIN} 时携带 {@code input}；失败时 {@code errMsg} 非空。</p>
 */
@Data
@Accessors(chain = true)
public class PrepareWorkInjuryInvokeResponse {

    /** 会话 id（yb_wi_invoke_session）。 */
    private String sessionId;
    /** 本步一次性令牌（submit 须回传）。 */
    private String stepToken;
    /** 本步 infno（9102/2206/…）。 */
    private String infno;
    /** 定点机构编码（Win 外壳，可空由 Win 补全）。 */
    private String fixmedinsCode;
    /** 操作员编号（Win 外壳）。 */
    private String operatorNo;
    /** Win 业务体 input Map。 */
    private Map<String, Object> input;
    /** {@link com.zxplat.plugin.wi.gateway.scheme.shared.model.WiNextAction} 码。 */
    private String nextAction;
    /** prepare 失败原因。 */
    private String errMsg;
}
