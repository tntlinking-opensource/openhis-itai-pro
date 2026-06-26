/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.catalog.support;

import com.openhis.chs.api.ChsInfNo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 13xx TAB 列映射回归：对齐国家平台 filename 列序 + OpenHIS {@code ReadCsv} 版本链。
 */
class CatalogFileParserAllInfnoTest {

    record InfnoFixture(
        String infno,
        int columnCount,
        int hilistCodeCol,
        String hilistCode,
        int hilistNameCol,
        String hilistName,
        int versionChainCol,
        String versionChainValue,
        String expectNextLocalVer) {
    }

    static Stream<InfnoFixture> fixtures() {
        return Stream.of(
            fixture1301(),
            fixture1302(),
            fixture1305(),
            fixture1306(),
            fixture1307(),
            fixture1308(),
            fixture1309(),
            fixture1314(),
            fixture1315());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void parseAndVersionChain_alignWithSpec(InfnoFixture f) {
        CatalogTabLineBuilder line = new CatalogTabLineBuilder(f.columnCount())
            .set(f.hilistCodeCol(), f.hilistCode())
            .set(f.hilistNameCol(), f.hilistName())
            .set(f.versionChainCol(), f.versionChainValue());

        byte[] textBytes = line.toTextBytes();
        var parsed = CatalogFileParser.parseTextBytes(f.infno(), textBytes);

        assertThat(parsed.rows()).hasSize(1);
        assertThat(parsed.rows().get(0).getHilistCode()).isEqualTo(f.hilistCode());
        assertThat(parsed.rows().get(0).getHilistName()).isEqualTo(f.hilistName());
        assertThat(parsed.nextVersion()).isEqualTo(f.expectNextLocalVer());
        assertThat(CatalogFileParser.peekNextLocalVerFromPackage(f.infno(), textBytes))
            .isEqualTo(f.expectNextLocalVer());

        var scanned = CatalogFileParser.scanTextPackageMetadata(f.infno(), textBytes);
        assertThat(scanned.dataLineCount()).isEqualTo(1);
        assertThat(scanned.nextLocalVer()).isEqualTo(f.expectNextLocalVer());
    }

    private static InfnoFixture fixture1301() {
        return new InfnoFixture(
            ChsInfNo.DRUG_CATALOG_DOWNLOAD, 92,
            0, "Y00000001234", 3, "阿莫西林",
            83, "X001_20220701000000_A", "X001_20220701000000_A");
    }

    private static InfnoFixture fixture1302() {
        return new InfnoFixture(
            ChsInfNo.HERB_CATALOG_DOWNLOAD, 33,
            0, "T001234", 1, "黄芪",
            18, "X001_20220701000000_A", "X001_20220701000000_A");
    }

    private static InfnoFixture fixture1305() {
        return new InfnoFixture(
            ChsInfNo.MEDICAL_SERVICE_CATALOG_DOWNLOAD, 20,
            0, "110100001", 9, "普通门诊诊察费",
            15, "F002_20220701000000_A", "F002_20220701000000_A");
    }

    private static InfnoFixture fixture1306() {
        return new InfnoFixture(
            ChsInfNo.MEDICAL_CONSUMABLE_CATALOG_DOWNLOAD, 71,
            0, "C001234", 4, "一次性注射器",
            70, "X001_20220701000000_A", "X001_20220701000000_A");
    }

    /** 规范 col11/12 = index 10/11；版本链 col23 = index 22（OpenHIS version_number） */
    private static InfnoFixture fixture1307() {
        return new InfnoFixture(
            ChsInfNo.DISEASE_CATALOG_DOWNLOAD, 28,
            10, "C00.0", 11, "唇恶性肿瘤",
            22, "J001_20230526000000_A", "J001_20230526000000_A");
    }

    /** 规范 col11/12 = index 10/11；版本链 index 22 */
    private static InfnoFixture fixture1308() {
        return new InfnoFixture(
            ChsInfNo.SURGERY_CATALOG_DOWNLOAD, 28,
            10, "00.01", 11, "头部切开",
            22, "J001_20230526000000_A", "J001_20230526000000_A");
    }

    /** 规范 col1/15 = index 0/14；版本链 col10 = index 9 */
    private static InfnoFixture fixture1309() {
        return new InfnoFixture(
            ChsInfNo.SPECIAL_DISEASE_CATALOG_DOWNLOAD, 16,
            0, "M00101", 14, "高血压",
            9, "2023052600001076", "2023052600001076");
    }

    /** 规范 col6/7 = index 5/6；版本链 col13 = index 12 */
    private static InfnoFixture fixture1314() {
        return new InfnoFixture(
            ChsInfNo.TCM_DISEASE_CATALOG_DOWNLOAD, 14,
            5, "BNP010", 6, "咳嗽病",
            12, "2023052600001076", "2023052600001076");
    }

    /** 规范 col2/3 = index 1/2；版本链 col13 = index 12（OpenHIS version） */
    private static InfnoFixture fixture1315() {
        return new InfnoFixture(
            ChsInfNo.TCM_SYNDROME_CATALOG_DOWNLOAD, 14,
            1, "Z001", 2, "风寒证",
            12, "2023052600001076", "2023052600001076");
    }
}
