/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.miniapp.domain.context;

import lombok.Builder;
import lombok.Getter;

/**
 * 小程序请求上下文（DM-MAP-AUTH）。
 *
 * <p>由 {@code MiniappAuthFilter} 注入；业务禁止自行解 JWT。</p>
 */
@Getter
@Builder
public class MiniappContext {

    private Long accountId;
    private String openid;
    private String sessionId;
    private Long patientId;
    private Long practitionerId;
    private Long tenantId;
    private String appId;

    private static final ThreadLocal<MiniappContext> HOLDER = new ThreadLocal<>();

    public static void set(MiniappContext context) {
        HOLDER.set(context);
    }

    public static MiniappContext get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
