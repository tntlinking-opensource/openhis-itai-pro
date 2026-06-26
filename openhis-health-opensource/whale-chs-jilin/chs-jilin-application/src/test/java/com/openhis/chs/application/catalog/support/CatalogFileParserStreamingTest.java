/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.catalog.support;

import com.openhis.chs.api.ChsInfNo;
import com.openhis.chs.domain.catalog.command.UpsertNationalCatalogRowCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogFileParserStreamingTest {

    record InfnoLineFixture(String infno, int columnCount, int hilistCodeCol, String hilistCode) {
    }

    static Stream<InfnoLineFixture> allInfnoFixtures() {
        return Stream.of(
            new InfnoLineFixture(ChsInfNo.DRUG_CATALOG_DOWNLOAD, 92, 0, "Y001"),
            new InfnoLineFixture(ChsInfNo.HERB_CATALOG_DOWNLOAD, 33, 0, "T001"),
            new InfnoLineFixture(ChsInfNo.MEDICAL_SERVICE_CATALOG_DOWNLOAD, 20, 0, "110100001"),
            new InfnoLineFixture(ChsInfNo.MEDICAL_CONSUMABLE_CATALOG_DOWNLOAD, 71, 0, "C001"),
            new InfnoLineFixture(ChsInfNo.DISEASE_CATALOG_DOWNLOAD, 28, 10, "C00.0"),
            new InfnoLineFixture(ChsInfNo.SURGERY_CATALOG_DOWNLOAD, 28, 10, "00.01"),
            new InfnoLineFixture(ChsInfNo.SPECIAL_DISEASE_CATALOG_DOWNLOAD, 16, 0, "M00101"),
            new InfnoLineFixture(ChsInfNo.TCM_DISEASE_CATALOG_DOWNLOAD, 14, 5, "BNP010"),
            new InfnoLineFixture(ChsInfNo.TCM_SYNDROME_CATALOG_DOWNLOAD, 14, 1, "Z001"));
    }

    @Test
    void parseTextBytesStreaming_flushesInBatches() {
        String infno = ChsInfNo.DRUG_CATALOG_DOWNLOAD;
        String line1 = tabLine(infno, 92, 0, "Y001");
        String line2 = tabLine(infno, 92, 0, "Y002");
        String line3 = tabLine(infno, 92, 0, "Y003");
        byte[] textBytes = (line1 + "\n" + line2 + "\n" + line3 + "\n").getBytes(StandardCharsets.UTF_8);

        List<List<UpsertNationalCatalogRowCommand>> batches = new ArrayList<>();
        String nextVer = CatalogFileParser.parseTextBytesStreaming(
            infno, textBytes, 2, (batch, runningNext) -> batches.add(new ArrayList<>(batch)));

        assertThat(batches).hasSize(2);
        assertThat(batches.get(0)).hasSize(2);
        assertThat(batches.get(1)).hasSize(1);
        assertThat(batches.get(0).get(0).getHilistCode()).isEqualTo("Y001");
        assertThat(batches.get(1).get(0).getHilistCode()).isEqualTo("Y003");
        assertThat(batches.get(0).get(0).getSourceLineNo()).isEqualTo(1);
        assertThat(nextVer).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allInfnoFixtures")
    void parseTextBytesStreaming_matchesFullParseRowCount(InfnoLineFixture fixture) {
        String line1 = tabLine(fixture.infno(), fixture.columnCount(), fixture.hilistCodeCol(), fixture.hilistCode());
        String line2 = tabLine(fixture.infno(), fixture.columnCount(), fixture.hilistCodeCol(), fixture.hilistCode() + "X");
        byte[] textBytes = (line1 + "\n" + line2 + "\n").getBytes(StandardCharsets.UTF_8);

        int[] count = new int[1];
        CatalogFileParser.parseTextBytesStreaming(
            fixture.infno(), textBytes, 500, (batch, runningNext) -> count[0] += batch.size());

        var full = CatalogFileParser.parseTextBytes(fixture.infno(), textBytes);
        assertThat(count[0]).isEqualTo(full.rows().size());
    }

    private static String tabLine(String infno, int columnCount, int hilistCodeCol, String hilistCode) {
        String[] cols = new String[columnCount];
        cols[hilistCodeCol] = hilistCode;
        if (ChsInfNo.DRUG_CATALOG_DOWNLOAD.equals(infno)) {
            cols[3] = "名称";
        }
        return String.join("\t", Arrays.stream(cols).map(CatalogTabLineBuilder::nullToEmpty).toList());
    }
}
