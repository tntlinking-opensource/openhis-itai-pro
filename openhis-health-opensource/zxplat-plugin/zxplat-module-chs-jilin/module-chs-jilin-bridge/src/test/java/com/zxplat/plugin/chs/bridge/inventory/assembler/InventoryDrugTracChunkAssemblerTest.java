/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.inventory.assembler;

import static org.assertj.core.api.Assertions.assertThat;

import com.zxplat.plugin.chs.domain.inventory.command.InvUploadLineCommand;
import com.zxplat.plugin.chs.domain.share.constants.ChsInventoryConstants;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** {@link InventoryDrugTracChunkAssembler} 拆批单元测试。 */
class InventoryDrugTracChunkAssemblerTest {

    @Test
    void splitKeepsSingleLineWhenWithinLimit() {
        InvUploadLineCommand line = baseLine(3, "3.0000");
        List<InvUploadLineCommand> chunks = InventoryDrugTracChunkAssembler.splitForPlatformLimit(
            line, ChsInventoryConstants.DRUGTRACINFO_MAX_PER_REQUEST);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).isSameAs(line);
    }

    @Test
    void split960ByHardLimit500IntoTwoChunks() {
        InvUploadLineCommand line = baseLine(960, "960.0000");
        List<InvUploadLineCommand> chunks = InventoryDrugTracChunkAssembler.splitForPlatformLimit(
            line, ChsInventoryConstants.DRUGTRACINFO_PLATFORM_HARD_LIMIT);
        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getDrugTracCodgs()).hasSize(500);
        assertThat(chunks.get(1).getDrugTracCodgs()).hasSize(460);
        assertThat(chunks.get(0).getCnt()).isEqualByComparingTo("500.0000");
        assertThat(chunks.get(1).getCnt()).isEqualByComparingTo("460.0000");
        assertThat(chunks.get(0).getFixmedinsBchno()).isEqualTo("CIR2026-08-050002-C01");
        assertThat(chunks.get(1).getFixmedinsBchno()).isEqualTo("CIR2026-08-050002-C02");
        assertThat(chunks.get(0).getCnt().add(chunks.get(1).getCnt())).isEqualByComparingTo("960.0000");
    }

    @Test
    void split960BySafeLimit100IntoTenChunks() {
        InvUploadLineCommand line = baseLine(960, "960.0000");
        List<InvUploadLineCommand> chunks = InventoryDrugTracChunkAssembler.splitForPlatformLimit(
            line, ChsInventoryConstants.DRUGTRACINFO_MAX_PER_REQUEST);
        assertThat(chunks).hasSize(10);
        assertThat(chunks.get(0).getDrugTracCodgs()).hasSize(100);
        assertThat(chunks.get(9).getDrugTracCodgs()).hasSize(60);
        assertThat(chunks.get(0).getCnt()).isEqualByComparingTo("100.0000");
        assertThat(chunks.get(9).getCnt()).isEqualByComparingTo("60.0000");
    }

    @Test
    void splitPreservesNegativeSign() {
        InvUploadLineCommand line = baseLine(600, "-600.0000");
        List<InvUploadLineCommand> chunks = InventoryDrugTracChunkAssembler.splitForPlatformLimit(
            line, ChsInventoryConstants.DRUGTRACINFO_PLATFORM_HARD_LIMIT);
        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getCnt()).isEqualByComparingTo("-500.0000");
        assertThat(chunks.get(1).getCnt()).isEqualByComparingTo("-100.0000");
    }

    /**
     * 构造带固定批次号与追溯码列表的测试行。
     *
     * @param codeCount 码条数
     * @param cnt       数量字符串（可负）
     * @return 命令行
     */
    private static InvUploadLineCommand baseLine(int codeCount, String cnt) {
        InvUploadLineCommand line = new InvUploadLineCommand();
        line.setFixmedinsBchno("CIR2026-08-050002");
        line.setSourceLineId(1L);
        line.setInvChgType("108");
        line.setCnt(new BigDecimal(cnt));
        List<String> codes = new ArrayList<>(codeCount);
        for (int i = 0; i < codeCount; i++) {
            codes.add(String.format("8%019d", i));
        }
        line.setDrugTracCodgs(codes);
        return line;
    }
}
