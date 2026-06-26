/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.catalog.support;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** 构造 13xx TAB 分隔样例行（0-based 列索引）。 */
final class CatalogTabLineBuilder {

    private final String[] cols;

    CatalogTabLineBuilder(int columnCount) {
        this.cols = new String[columnCount];
    }

    CatalogTabLineBuilder set(int index, String value) {
        if (index >= 0 && index < cols.length) {
            cols[index] = value;
        }
        return this;
    }

    byte[] toTextBytes() {
        String line = String.join("\t", Arrays.stream(cols).map(CatalogTabLineBuilder::nullToEmpty).toList());
        return (line + "\n").getBytes(StandardCharsets.UTF_8);
    }

    static String nullToEmpty(String value) {
        if (value == null) {
            return "";
        }
        return value;
    }
}
