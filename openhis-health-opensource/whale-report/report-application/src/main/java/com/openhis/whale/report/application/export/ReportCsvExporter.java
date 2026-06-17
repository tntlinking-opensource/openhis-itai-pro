package com.openhis.whale.report.application.export;

import com.openhis.whale.report.application.query.response.ReportColumnDto;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public final class ReportCsvExporter {

    private static final byte[] UTF8_BOM = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private ReportCsvExporter() {
    }

    public static byte[] toCsvBytes(List<ReportColumnDto> columns, List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder();
        if (columns != null && !columns.isEmpty()) {
            for (int i = 0; i < columns.size(); i += 1) {
                ReportColumnDto col = columns.get(i);
                String header = col.getField();
                if (col.getLabel() != null) {
                    header = col.getLabel();
                }
                sb.append(escapeCsv(header));
                if (i < columns.size() - 1) {
                    sb.append(',');
                }
            }
            sb.append('\n');
            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    for (int i = 0; i < columns.size(); i += 1) {
                        String field = columns.get(i).getField();
                        Object value = null;
                        if (field != null && row != null) {
                            value = row.get(field);
                        }
                        String cellText = "";
                        if (value != null) {
                            cellText = String.valueOf(value);
                        }
                        sb.append(escapeCsv(cellText));
                        if (i < columns.size() - 1) {
                            sb.append(',');
                        }
                    }
                    sb.append('\n');
                }
            }
        }
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] combined = new byte[UTF8_BOM.length + body.length];
        System.arraycopy(UTF8_BOM, 0, combined, 0, UTF8_BOM.length);
        System.arraycopy(body, 0, combined, UTF8_BOM.length, body.length);
        return combined;
    }

    private static String escapeCsv(String value) {
        String text = "";
        if (value != null) {
            text = value;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
