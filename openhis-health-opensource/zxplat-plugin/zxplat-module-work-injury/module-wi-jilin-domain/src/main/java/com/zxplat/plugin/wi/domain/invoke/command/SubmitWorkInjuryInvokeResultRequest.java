/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.domain.invoke.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

/**
 * submitInvokeResult 请求（POST /api/app/workInjuryInvoke/submitInvokeResult）。
 *
 * <p>Win 回写后由前端提交；{@code winRawInput} 写入 {@code yb_invoke_log.request_json}。</p>
 */
@Data
@Accessors(chain = true)
public class SubmitWorkInjuryInvokeResultRequest {

    /** prepare 下发的 sessionId。 */
    private String sessionId;
    /** prepare 下发的 stepToken（一次性）。 */
    private String stepToken;
    /** Win/DLL infcode 是否为成功。 */
    private boolean success;
    /** 平台返回码 infcode（0 为成功）。 */
    private String infcode;
    /** Win/DLL 或平台 err_msg。 */
    private String errMsg;
    /** 平台 output Map（按 infno 解析）。 */
    private Map<String, Object> output;
    /** Win msgid（2207 写 msgid_2207）。 */
    private String winMsgid;

    /** OP_SETTLE submit 时可回传 chargeItemIds 等 context。 */
    private Map<String, Object> context;

    /** Win 实际发送的 inputdata JSON（审计 request_json）。 */
    private String winRawInput;
}
