/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.catalog.support;

import com.openhis.chs.api.ChsInfNo;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogFileParser1307Test {

    @Test
    void peekNextLocalVer_usesCol22LikeOpenHisVersionNumber() throws Exception {
        Path sample = Path.of("src/test/resources/sample-1307-line.txt");
        byte[] textBytes = Files.readAllBytes(sample);

        assertThat(CatalogFileParser.peekNextLocalVerFromPackage(ChsInfNo.DISEASE_CATALOG_DOWNLOAD, textBytes))
            .isEqualTo("J001_20230526000000_A");
        assertThat(CatalogFileParser.peekLastVersionNoFromTextBytes(ChsInfNo.DISEASE_CATALOG_DOWNLOAD, textBytes))
            .isEqualTo("J001_20230526000000_A");
    }

    @Test
    void parseText_readsDiagnosisCodeFromCol11NotChapterCols() throws Exception {
        Path sample = Path.of("src/test/resources/sample-1307-line.txt");
        byte[] textBytes = Files.readAllBytes(sample);
        var result = CatalogFileParser.parseTextBytes(ChsInfNo.DISEASE_CATALOG_DOWNLOAD, textBytes);

        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).getHilistCode()).isEqualTo("C00.0");
        assertThat(result.rows().get(0).getHilistName()).isEqualTo("唇恶性肿瘤");
        assertThat(result.nextVersion()).isEqualTo("J001_20230526000000_A");
    }
}
