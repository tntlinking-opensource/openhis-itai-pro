/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.foundation.sign;

import com.zxplat.plugin.chs.domain.session.access.InsuranceSignInAccess;
import com.zxplat.plugin.chs.domain.session.command.SignInCommand;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import com.zxplat.plugin.chs.gateway.session.InsuranceSignSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【9001】医保签到 bridge 出站实现（P15）。
 *
 * <p><b>职责：</b>将 {@link SignInCommand} 映射为 gateway {@link com.zxplat.plugin.chs.gateway.client.InvokeContext} 并委托 {@link com.zxplat.plugin.chs.gateway.session.InsuranceSignSessionService}。</p>
 * <p><b>实现端口：</b>{@link com.zxplat.plugin.chs.domain.session.access.InsuranceSignInAccess}</p>
 * <p><b>调用方：</b>{@code InsuranceSignAppService}</p>
 * <p><b>架构边界：</b>bridge 层组装 gateway 出站上下文；application 只依赖 domain 端口。</p>
 * <p><b>迁移阶段：</b>P15</p>
 */
@Component
@RequiredArgsConstructor
public class InsuranceSignInAccessImpl implements InsuranceSignInAccess {

    private final InsuranceSignSessionService signSessionService;

    @Override
    public String signIn(SignInCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("签到命令不能为空");
        }
        InvokeContext context = InvokeContext.builder()
            .operatorId(command.getOperatorId())
            .operatorName(command.getOperatorName())
            .signMac(command.getSignMac())
            .signIp(command.getSignIp())
            .build();
        return signSessionService.signIn(command.getContractBusNo(), context);
    }
}
