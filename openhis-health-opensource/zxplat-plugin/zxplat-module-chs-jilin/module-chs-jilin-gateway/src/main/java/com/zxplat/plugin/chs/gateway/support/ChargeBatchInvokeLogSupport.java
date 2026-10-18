/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.support;

import com.zxplat.plugin.chs.domain.settlement.model.ChargeBatch;
import com.zxplat.plugin.chs.gateway.observability.ChsInvokeLogContext;
import com.zxplat.plugin.chs.gateway.observability.ChsInvokeLogContextHolder;

/** 将 {@link ChargeBatch} 编排键写入 invoke 日志上下文 */
public final class ChargeBatchInvokeLogSupport {

    private ChargeBatchInvokeLogSupport() {
    }

    public static ChsInvokeLogContext contextFromBatch(ChargeBatch batch, Long tenantId) {
        ChsInvokeLogContext ctx = new ChsInvokeLogContext().setTenantId(tenantId);
        applyBatch(ctx, batch);
        return ctx;
    }

    public static void mergeBatch(ChargeBatch batch) {
        if (batch == null) {
            return;
        }
        ChsInvokeLogContextHolder.merge(ctx -> applyBatch(ctx, batch));
    }

    private static void applyBatch(ChsInvokeLogContext ctx, ChargeBatch batch) {
        if (batch == null || ctx == null) {
            return;
        }
        ctx.setChrgBchno(batch.batchKey());
        ctx.setPreSettleId(batch.preSettleId());
        if (batch.paymentDraftId() != null) {
            ctx.setPaymentDraftId(batch.paymentDraftId());
        }
    }
}
