/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.catalog.support;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogFileParserTest {

    private static final String INFNO_1301 = "1301";

    @Test
    void parseZip_readsTabDelimitedRowsAndNextVersion() throws Exception {
        String[] cols = new String[92];
        cols[0] = "Y00000001234";
        cols[3] = "阿莫西林";
        cols[9] = "片剂";
        cols[13] = "0.25g*24";
        cols[36] = "2020-01-01";
        cols[37] = "2099-12-31";
        cols[50] = "AMXL";
        cols[61] = "国药准字H123";
        cols[78] = "1";
        cols[82] = "V00000000000000000001";
        cols[83] = "V00000000000000000002";
        cols[91] = "甲";
        String line = String.join("\t", Arrays.stream(cols).map(CatalogTabLineBuilder::nullToEmpty).toList());
        byte[] zipBytes = zipSingleText("catalog.txt", line + "\n");

        var result = CatalogFileParser.parseZip(INFNO_1301, zipBytes);

        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).getHilistCode()).isEqualTo("Y00000001234");
        assertThat(result.nextVersion()).isEqualTo("V00000000000000000002");

        byte[] textBytes = CatalogFileParser.unzipToTextBytes(zipBytes);
        assertThat(CatalogFileParser.peekNextLocalVerFromPackage(INFNO_1301, textBytes))
            .isEqualTo("V00000000000000000002");
    }

    @Test
    void peekNextLocalVer_usesCol83LikeOpenHis() throws Exception {
        String[] line1 = new String[92];
        line1[0] = "A001";
        line1[82] = "2022070100215422";
        line1[83] = "X001_20220701000000_A";
        String[] line2 = new String[92];
        line2[0] = "A002";
        line2[82] = "2022070100215537";
        line2[83] = "X001_20220701000000_A";
        String[] line3 = new String[92];
        line3[0] = "A003";
        line3[82] = "2022070100215555";
        line3[83] = "X001_20220701000000_A";
        String content = String.join("\t", Arrays.stream(line1).map(CatalogTabLineBuilder::nullToEmpty).toList()) + "\n"
            + String.join("\t", Arrays.stream(line2).map(CatalogTabLineBuilder::nullToEmpty).toList()) + "\n"
            + String.join("\t", Arrays.stream(line3).map(CatalogTabLineBuilder::nullToEmpty).toList()) + "\n";
        byte[] textBytes = CatalogFileParser.unzipToTextBytes(
            zipSingleText("catalog.txt", content));

        assertThat(CatalogFileParser.peekMaxVersionNoFromTextBytes(INFNO_1301, textBytes))
            .isEqualTo("2022070100215555");
        assertThat(CatalogFileParser.peekNextLocalVerFromPackage(INFNO_1301, textBytes))
            .isEqualTo("X001_20220701000000_A");
    }

    @Test
    void peekNextLocalVer_fallsBackToMaxCol82WhenCol83Missing() throws Exception {
        String[] cols = new String[92];
        cols[0] = "Y00000001234";
        cols[82] = "2022070100215537";
        String line = String.join("\t", Arrays.stream(cols).map(CatalogTabLineBuilder::nullToEmpty).toList());
        byte[] textBytes = CatalogFileParser.unzipToTextBytes(
            zipSingleText("catalog.txt", line + "\n"));

        assertThat(CatalogFileParser.peekNextLocalVerFromPackage(INFNO_1301, textBytes))
            .isEqualTo("2022070100215537");
    }

    private static byte[] zipSingleText(String entryName, String content) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return buffer.toByteArray();
    }
}
