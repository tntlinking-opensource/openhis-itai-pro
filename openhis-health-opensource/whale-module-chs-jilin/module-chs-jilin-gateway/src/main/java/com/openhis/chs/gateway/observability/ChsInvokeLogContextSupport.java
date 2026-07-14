/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.observability;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 读取 ThreadLocal 中的网关 invoke 元数据。
 *
 * <p>由 {@link PersistingInsuranceGatewayClient} 在 invoke 前后写入，
 * 供结算落库时关联 {@code yb_invoke_log.id} 与 msgid。</p>
 */
public final class ChsInvokeLogContextSupport {

    private ChsInvokeLogContextSupport() {
    }

    /** 当前线程最近一次平台调用的 msgid（2601 冲正 omsgid 来源） */
    public static String currentMsgid() {
        ChsInvokeLogContext ctx = ChsInvokeLogContextHolder.get();
        if (ctx == null) {
            return null;
        }
        return trimToNull(ctx.getMsgid());
    }

    /** 当前线程最近一次 {@code yb_invoke_log.id} */
    public static Long currentInvokeLogId() {
        ChsInvokeLogContext ctx = ChsInvokeLogContextHolder.get();
        if (ctx == null) {
            return null;
        }
        return ctx.getLastInvokeLogId();
    }

    /** 当前线程最近一次平台交易号 infno */
    public static String currentInfno() {
        ChsInvokeLogContext ctx = ChsInvokeLogContextHolder.get();
        if (ctx == null) {
            return null;
        }
        return trimToNull(ctx.getInfno());
    }
}
