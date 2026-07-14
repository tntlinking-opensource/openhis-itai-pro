/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.bridge.settle.invoker;

import com.openhis.wi.bridge.settle.support.WorkInjuryPreSettleRefreshSupport;
import com.openhis.wi.domain.settle.access.RefreshWorkInjuryPreSettleAmountsAccess;
import com.openhis.wi.domain.settle.command.RefreshWorkInjuryPreSettleAmountsCommand;
import com.openhis.wi.domain.settle.result.RefreshWorkInjuryPreSettleAmountsResult;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 2206 后刷新收银草稿应付金额端口实现。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.work-injury.enabled", havingValue = "true")
public class RefreshWorkInjuryPreSettleAmountsAccessImpl implements RefreshWorkInjuryPreSettleAmountsAccess {

    private final WorkInjuryPreSettleRefreshSupport workInjuryPreSettleRefreshSupport;

    @Override
    public RefreshWorkInjuryPreSettleAmountsResult refresh(RefreshWorkInjuryPreSettleAmountsCommand command) {
        return workInjuryPreSettleRefreshSupport.refresh(command);
    }
}
