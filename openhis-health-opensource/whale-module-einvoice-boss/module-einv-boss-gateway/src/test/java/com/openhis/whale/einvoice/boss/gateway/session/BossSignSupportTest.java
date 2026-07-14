/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.gateway.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link BossSignSupport} 签名向量测试。
 */
class BossSignSupportTest {

    @Test
    void sign_matchesBossSpecConcatenation() {
        String sign = BossSignSupport.sign("app1", "ewogI", "ibuaiVcKdpRxkhJA", "192006250b4c09247ec02f6a2d");
        assertEquals("00DA962EC3C3879C36B368C736DB1C61", sign);
    }
}
