/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.domain.share;

/**
 * 工伤模块业务错误码（前缀 WI-；见 08-HLD / 09-LLD）。
 */
public final class WiErrorCodes {

    /** 模块未启用或未装配 wi-jilin-starter。 */
    public static final String MODULE_DISABLED = "WI-001";
    /** 本机 OpenHIS.Win :5000 不可达。 */
    public static final String WIN_UNAVAILABLE = "WI-002";
    /** 缺少 qualification_id 或审批未生效（9102 未完成）。 */
    public static final String NO_QUALIFICATION = "WI-003";
    /** 2301 后未再完成 9102 确认（BR-WI-REG-004）。 */
    public static final String APPROVAL_NOT_FOLLOWED_BY_9102 = "WI-004";
    /** 挂号费须纯自费（工伤不走统筹）。 */
    public static final String REG_FEE_MUST_SELF_PAY = "WI-005";
    /** invoke 会话 / phase / context 无效。 */
    public static final String INVALID_INVOKE_SESSION = "WI-006";
    /** Win/DLL infcode 非 0。 */
    public static final String PLATFORM_INFCODE_FAILED = "WI-007";
    /** prepare 的 step_token 与 submit 不匹配或已消费。 */
    public static final String STEP_TOKEN_MISMATCH = "WI-008";
    /** 已有工伤结算回执与本次收费不一致，禁止复用/改绑。 */
    public static final String SETTLEMENT_REUSE_FORBIDDEN = "WI-009";

    private WiErrorCodes() {
    }
}
