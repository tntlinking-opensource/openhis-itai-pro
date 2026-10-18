/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.gateway.scheme.share.parser;

import com.zxplat.plugin.einvoice.boss.domain.invoke.result.BossInvokeResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BossResponseParser} 响应解码测试。
 */
class BossResponseParserTest {

    private final BossResponseParser parser = new BossResponseParser();

    @Test
    void parse_percentEncodesBinaryTokenInPictureUrl() {
        byte[] business = ("{\"billBatchCode\":\"123\",\"billNo\":\"1001651756\",\"random\":\"053ff1\","
                + "\"createTime\":\"20260629224723940\","
                + "\"pictureUrl\":\"http://cloud.chinaebill.cn/saas-industry-04/ebill.html?t="
                + "\u00a1_\u00fa\u00eaC\u00dek\u00feX\u00f8O\u00ef\u00d3\u00c9\"}")
                .getBytes(StandardCharsets.ISO_8859_1);
        String messageB64 = Base64.getEncoder().encodeToString(business);
        String inner = "{\"result\":\"S0000\",\"message\":\"" + messageB64 + "\"}";
        String outer = "{\"data\":\"" + Base64.getEncoder().encodeToString(inner.getBytes(StandardCharsets.UTF_8)) + "\"}";

        BossInvokeResult result = parser.parse(outer);

        assertTrue(result.isSuccess());
        assertEquals("1001651756", result.getBillNo());
        String pictureUrl = result.getPictureUrl();
        assertTrue(pictureUrl.startsWith("http://cloud.chinaebill.cn/saas-industry-04/ebill.html?t="));
        assertTrue(pictureUrl.contains("%A1"), pictureUrl);
        assertTrue(pictureUrl.chars().noneMatch(c -> c > 127), pictureUrl);
    }

    @Test
    void parse_decodesUtf8RejectMessageInsteadOfLatin1Mojibake() {
        String rejectText = "EYD-COM-0303:请先上报数据，才能继续开票:单位开票数据已超过审核周期";
        String messageB64 = Base64.getEncoder().encodeToString(rejectText.getBytes(StandardCharsets.UTF_8));
        String inner = "{\"result\":\"E9999\",\"message\":\"" + messageB64 + "\"}";
        String outer = "{\"data\":\"" + Base64.getEncoder().encodeToString(inner.getBytes(StandardCharsets.UTF_8)) + "\"}";

        BossInvokeResult result = parser.parse(outer);

        assertEquals("EYD-COM-0303", result.getResultCode());
        assertEquals(rejectText, result.getRawMessage());
        assertTrue(result.getRawMessage().contains("请先上报数据"));
    }
}
