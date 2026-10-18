/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.inventory.assembler;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.chs.domain.share.constants.ChsInventoryConstants;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

/** {@link InventoryDrugTracJsonChunkAssembler} 已落库报文拆批单元测试。 */
class InventoryDrugTracJsonChunkAssemblerTest {

    @Test
    void exceedsLimitWhenOverSafeMax() {
        String json = build3502Json("CIR2026-08-050002", 101, "101.0000");
        assertThat(InventoryDrugTracJsonChunkAssembler.exceedsPlatformDrugTracLimit(json)).isTrue();
    }

    @Test
    void split960BySafeLimitIntoTenChunks() {
        String json = build3502Json("CIR2026-08-050002", 960, "960.0000");
        List<String> chunks = InventoryDrugTracJsonChunkAssembler.split3502RequestJson(
            json, ChsInventoryConstants.DRUGTRACINFO_MAX_PER_REQUEST);
        assertThat(chunks).hasSize(10);
        JSONObject inv0 = JSON.parseObject(chunks.get(0)).getJSONObject("invinfo");
        JSONObject inv9 = JSON.parseObject(chunks.get(9)).getJSONObject("invinfo");
        assertThat(inv0.getJSONArray("drugtracinfo")).hasSize(100);
        assertThat(inv9.getJSONArray("drugtracinfo")).hasSize(60);
        assertThat(inv0.getString("fixmedins_bchno")).isEqualTo("CIR2026-08-050002-C01");
        assertThat(inv9.getString("fixmedins_bchno")).isEqualTo("CIR2026-08-050002-C10");
        assertThat(inv0.getBigDecimal("cnt")).isEqualByComparingTo("100.0000");
        assertThat(inv9.getBigDecimal("cnt")).isEqualByComparingTo("60.0000");
        assertThat(InventoryDrugTracJsonChunkAssembler.readFixmedinsBchno(chunks.get(0)))
            .isEqualTo("CIR2026-08-050002-C01");
    }

    /**
     * 构造最小 3502 request_json（仅 invinfo + drugtracinfo）。
     *
     * @param bchno     批次号
     * @param codeCount 追溯码条数
     * @param cnt       数量字符串
     * @return JSON 文本
     */
    private static String build3502Json(String bchno, int codeCount, String cnt) {
        StringBuilder codes = new StringBuilder();
        for (int i = 0; i < codeCount; i++) {
            if (i > 0) {
                codes.append(',');
            }
            codes.append("{\"drug_trac_codg\":\"8")
                .append(String.format("%019d", i))
                .append("\"}");
        }
        return "{\"invinfo\":{"
            + "\"fixmedins_bchno\":\"" + bchno + "\","
            + "\"cnt\":" + new BigDecimal(cnt).toPlainString() + ","
            + "\"inv_chg_type\":\"108\","
            + "\"drugtracinfo\":[" + codes + "]"
            + "}}";
    }
}
