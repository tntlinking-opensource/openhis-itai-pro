/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.observability;

import java.util.function.Consumer;

/** 当前线程医保网关调用日志上下文 */
public final class ChsInvokeLogContextHolder {

    private static final ThreadLocal<ChsInvokeLogContext> CONTEXT = new ThreadLocal<>();

    private ChsInvokeLogContextHolder() {
    }

    public static void set(ChsInvokeLogContext context) {
        CONTEXT.set(context);
    }

    public static ChsInvokeLogContext get() {
        return CONTEXT.get();
    }

    public static void merge(Consumer<ChsInvokeLogContext> mutator) {
        ChsInvokeLogContext context = CONTEXT.get();
        if (context == null) {
            context = new ChsInvokeLogContext();
            CONTEXT.set(context);
        }
        mutator.accept(context);
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
