/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.upload.hqms.support;

import com.openhis.health.application.mrms.upload.hqms.HqmsColumns;
import com.openhis.health.application.mrms.upload.hqms.HqmsConstants;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

/**
 * HQMS CSV 写出（GBK、逗号分隔、RFC 式引号转义、{@code \r\n} 行尾）。
 *
 * <p>编码对齐国基 {@code exportCSV2} / {@code cvsExport_HQMS}。</p>
 */
public final class HqmsCsvWriter {

    private static final String COL_SEP = ",";

    private static final String ROW_SEP = "\r\n";

    /** HQMS CSV 文件编码（GBK）。 */
    private static final Charset CSV_CHARSET = Charset.forName(HqmsConstants.CSV_CHARSET_NAME);

    /** 禁止实例化。 */
    private HqmsCsvWriter() {
    }

    /**
     * 将多行 Map（键=列名）写成 CSV 字节。
     *
     * @param rows 导出行；每行须含 {@link HqmsColumns#ORDERED} 各键（缺则空）
     * @return GBK CSV 字节
     */
    public static byte[] write(List<Map<String, String>> rows) {
        StringBuilder buf = new StringBuilder(16384);
        appendRow(buf, HqmsColumns.ORDERED);
        if (rows != null) {
            for (Map<String, String> row : rows) {
                String[] cells = new String[HqmsColumns.ORDERED.length];
                for (int i = 0; i < HqmsColumns.ORDERED.length; i++) {
                    String key = HqmsColumns.ORDERED[i];
                    String val = "";
                    if (row != null && row.get(key) != null) {
                        val = row.get(key);
                    }
                    cells[i] = val;
                }
                appendRow(buf, cells);
            }
        }
        return buf.toString().getBytes(CSV_CHARSET);
    }

    /**
     * 追加一行（含行尾）。
     *
     * @param buf   缓冲
     * @param cells 单元格
     */
    private static void appendRow(StringBuilder buf, String[] cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                buf.append(COL_SEP);
            }
            buf.append(escape(cells[i]));
        }
        buf.append(ROW_SEP);
    }

    /**
     * CSV 字段转义：含逗号/引号/换行/前导 TAB 时用双引号包裹。
     *
     * @param raw 原始单元格
     * @return 转义后文本
     */
    private static String escape(String raw) {
        String s = raw;
        if (s == null) {
            s = "";
        }
        boolean needQuote = false;
        if (!s.isEmpty() && s.charAt(0) == '\t') {
            needQuote = true;
        }
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            needQuote = true;
        }
        if (!needQuote) {
            return s;
        }
        StringBuilder out = new StringBuilder(s.length() + 8);
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') {
                out.append('"').append('"');
            } else {
                out.append(c);
            }
        }
        out.append('"');
        return out.toString();
    }
}
