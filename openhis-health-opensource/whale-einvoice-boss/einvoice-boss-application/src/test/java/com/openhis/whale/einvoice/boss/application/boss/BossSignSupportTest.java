package com.openhis.whale.einvoice.boss.application.boss;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link BossSignSupport} 签名向量测试。
 */
class BossSignSupportTest {

    @Test
    void sign_matchesBossSpecConcatenation() {
        String sign = BossSignSupport.sign("app1", "ewogI", "ibuaiVcKdpRxkhJA", "192006250b4c09247ec02f6a2d");
        // 规范文档样例 sign 为占位值；按 stringA+key+version 拼接后 MD5 结果如下
        assertEquals("00DA962EC3C3879C36B368C736DB1C61", sign);
    }
}
