/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.extension.miniapp;

import java.util.Optional;

/**
 * 小程序医护身份查档（按院内工号）。
 */
public interface MiniappPractitionerLookupPort {

    /**
     * 按 {@code adm_practitioner.bus_no} 解析在职医护。
     *
     * @param tenantId 租户（预留；当前 practitioners 按院端租户隔离）
     * @param busNo    工号
     */
    Optional<MiniappPractitionerLookupView> resolveByBusNo(Long tenantId, String busNo);
}
