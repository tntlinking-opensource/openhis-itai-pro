/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.settlement;

import com.openhis.chs.application.gateway.ChsInvokeLogContext;
import com.openhis.chs.application.gateway.ChsInvokeLogContextHolder;
import com.openhis.chs.domain.settlement.model.ChargeBatch;

/** 将 {@link ChargeBatch} 编排键写入 invoke 日志上下文。 */
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
