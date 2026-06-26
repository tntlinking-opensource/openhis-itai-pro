package com.openhis.whale.report.application.report.support;

import com.openhis.whale.report.domain.support.ReportQueryValidator;
import com.openhis.whale.report.domain.support.ReportTextSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读 SQL 执行器：NamedParameterJdbcTemplate + {@link ReportQueryValidator} 白名单校验。
 *
 * <p>所有单元格 null 转为 {@code "—"} 字符串，便于前端/Excel 展示一致。</p>
 */
@Component
@ConditionalOnClass(DataSource.class)
public class ReportQueryExecutor {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ReportQueryExecutor(DataSource dataSource) {
        this.jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
    }

    /**
     * 执行 SQL 并返回列标签 + 行数据（最多 maxRows 行）。
     *
     * @param sql     已通过 {@link ReportQueryValidator} 的 SELECT
     * @param params  命名参数（如 dateFrom、dateTo、keyword）
     * @param maxRows 结果集上限，超出截断
     * @return 列标签与行数据
     */
    public ReportSqlQueryResult execute(String sql, Map<String, Object> params, int maxRows) {
        ReportQueryValidator.validate(sql);
        Map<String, Object> bound = Map.of();
        if (params != null) {
            bound = new LinkedHashMap<>(params);
        }

        return jdbcTemplate.query(
                sql,
                bound,
                rs -> {
                    ResultSetMetaData meta = rs.getMetaData();
                    int columnCount = meta.getColumnCount();
                    List<String> fields = new ArrayList<>(columnCount);
                    for (int i = 1; i <= columnCount; i += 1) {
                        fields.add(meta.getColumnLabel(i));
                    }

                    List<Map<String, Object>> rows = new ArrayList<>();
                    while (rs.next()) {
                        if (rows.size() >= maxRows) {
                            // 达内存行数上限，停止拉取
                            break;
                        }
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= columnCount; i += 1) {
                            String label = meta.getColumnLabel(i);
                            Object value = JdbcUtils.getResultSetValue(rs, i);
                            row.put(label, ReportTextSupport.displayCell(value));
                        }
                        rows.add(row);
                    }
                    return new ReportSqlQueryResult(fields, rows);
                });
    }

    public record ReportSqlQueryResult(List<String> fields, List<Map<String, Object>> rows) {

        public ReportSqlQueryResult {
            List<String> safeFields = List.of();
            if (fields != null) {
                safeFields = List.copyOf(fields);
            }
            fields = safeFields;
            List<Map<String, Object>> safeRows = List.of();
            if (rows != null) {
                safeRows = List.copyOf(rows);
            }
            rows = safeRows;
        }
    }
}
