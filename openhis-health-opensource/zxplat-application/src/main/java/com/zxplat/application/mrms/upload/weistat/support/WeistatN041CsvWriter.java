/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.upload.weistat.support;

import com.zxplat.application.mrms.upload.weistat.WeistatN041Columns;
import com.zxplat.application.mrms.upload.weistat.WeistatN041Constants;
import com.zxplat.framework.core.application.util.StringUtils;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

/**
 * 卫统 N041 CSV 写出（GBK、逗号分隔、RFC 式引号转义、{@code \r\n} 行尾）。
 *
 * <p>编码对齐国基 HQMS 写出与中文 Windows Excel 默认打开码页；勿用无 BOM 的 UTF-8，
 * 否则 Excel 按 GBK 误读会产生乱码，并可能把 UTF-8 多字节误当成逗号导致错列。</p>
 */
public final class WeistatN041CsvWriter {

    private static final String COL_SEP = ",";

    private static final String ROW_SEP = "\r\n";

    /** 卫统 CSV 文件编码（GBK）。 */
    private static final Charset CSV_CHARSET = Charset.forName(WeistatN041Constants.CSV_CHARSET_NAME);

    /** 禁止实例化。 */
    private WeistatN041CsvWriter() {
    }

    /**
     * 将多行 Map（键=列名）写成 CSV 字节。
     *
     * @param rows 导出行；每行须含 {@link WeistatN041Columns#ORDERED} 各键（缺则空）
     * @return GBK CSV 字节
     */
    public static byte[] write(List<Map<String, String>> rows) {
        StringBuilder buf = new StringBuilder(4096);
        // 表头
        appendRow(buf, WeistatN041Columns.ORDERED);
        if (rows != null) {
            for (Map<String, String> row : rows) {
                String[] cells = new String[WeistatN041Columns.ORDERED.length];
                for (int i = 0; i < WeistatN041Columns.ORDERED.length; i++) {
                    String key = WeistatN041Columns.ORDERED[i];
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
     * CSV 字段转义：含逗号/引号/换行/前导 TAB 时用双引号包裹，内部引号加倍。
     *
     * <p>前导 TAB（Excel 文本保护）必须加引号，否则 TAB 可能被吞或列错位。</p>
     *
     * @param raw 原始文本
     * @return 可直接写入 CSV 的字段
     */
    private static String escape(String raw) {
        String value = StringUtils.nullToEmpty(raw);
        boolean needQuote = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0
                || value.indexOf('\t') >= 0
                || looksLikeLongNumericToken(value);
        if (!needQuote) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    /**
     * 是否像会被 Excel 收成科学计数的长数字令牌（已含 TAB 前缀时也视为需引号）。
     *
     * @param value 单元格文本
     * @return true 须强制引号
     */
    private static boolean looksLikeLongNumericToken(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        String t = value;
        if (t.charAt(0) == '\t') {
            t = t.substring(1);
        }
        if (t.matches("\\d{11,}")) {
            return true;
        }
        if (t.matches("\\d{15}")) {
            return true;
        }
        return t.matches("\\d{17}[0-9Xx]");
    }
}
