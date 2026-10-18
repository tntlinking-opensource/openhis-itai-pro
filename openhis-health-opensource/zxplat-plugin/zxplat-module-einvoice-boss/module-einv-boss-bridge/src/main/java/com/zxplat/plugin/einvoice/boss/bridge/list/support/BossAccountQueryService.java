/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.bridge.list.support;

import com.zxplat.plugin.einvoice.boss.domain.list.access.BossAccountStatusQueryAccess;
import com.zxplat.plugin.einvoice.boss.domain.invoke.access.BossInvokeLogAccess;
import com.zxplat.plugin.einvoice.boss.bridge.list.invoker.EinvAccountStatusInvoker;
import com.zxplat.plugin.einvoice.boss.domain.share.BossServiceIds;
import com.zxplat.plugin.einvoice.boss.gateway.scheme.list.assembler.AccountStatusPayloadAssembler;
import com.zxplat.plugin.einvoice.boss.domain.list.result.BossAccountStatusResult;
import com.zxplat.plugin.einvoice.boss.gateway.scheme.list.parser.AccountStatusOutputParser;
import com.zxplat.plugin.einvoice.boss.domain.invoke.result.BossInvokeResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * list cap 入账状态查询：委托 bridge {@link EinvAccountStatusInvoker} + gateway parser。
 */
@Service
@RequiredArgsConstructor
public class BossAccountQueryService implements BossAccountStatusQueryAccess {

    private final EinvAccountStatusInvoker accountStatusInvoker;
    private final AccountStatusPayloadAssembler accountStatusPayloadAssembler;
    private final AccountStatusOutputParser accountStatusOutputParser;
    private final BossInvokeLogAccess invokeLogWriter;

    /**
     * 按票面信息查询入账状态。
     */
    @Override
    public BossAccountStatusResult query(Long tenantId, String billBatchCode, String billNo) {
        String requestJson = accountStatusPayloadAssembler.assemble(billBatchCode, billNo);
        long started = System.currentTimeMillis();
        BossInvokeResult invokeResult = accountStatusInvoker.invoke(requestJson);
        int durationMs = (int) (System.currentTimeMillis() - started);
        invokeLogWriter.writeWithoutTask(
                tenantId,
                BossServiceIds.GET_E_BILL_ACCOUNT_STATUS,
                billNo,
                invokeResult,
                durationMs,
                requestJson,
                invokeResult.getRawMessage());
        return accountStatusOutputParser.parse(invokeResult);
    }
}
