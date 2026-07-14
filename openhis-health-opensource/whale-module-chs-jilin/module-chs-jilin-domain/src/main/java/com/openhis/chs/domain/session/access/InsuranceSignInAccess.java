/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.session.access;

import com.openhis.chs.domain.session.command.SignInCommand;

/**
 * 【9001】医保签到出站端口。
 *
 * <p><b>职责：</b>定义医保签到（9001）的 domain 出站契约，返回平台 sign_no。</p>
 * <p><b>实现位置：</b>{@code com.openhis.chs.bridge.foundation.sign.InsuranceSignInAccessImpl}</p>
 * <p><b>调用方：</b>{@code InsuranceSignAppService}</p>
 * <p><b>架构边界：</b>application 只依赖本包接口，禁止直接引用 {@code com.openhis.chs.gateway.*}</p>
 * <p><b>迁移阶段：</b>P15</p>
 */
public interface InsuranceSignInAccess {

    /**
     * 执行医保签到并返回签到流水号。
     *
     * @param command 签到命令（含契约编号与操作员/MAC/IP）
     * @return 平台 sign_no
     */
    String signIn(SignInCommand command);
}
