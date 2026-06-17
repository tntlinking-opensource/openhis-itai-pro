package com.openhis.whale.report.application.query.support;

import com.openhis.whale.core.application.exception.ValidationException;
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

@Component
@ConditionalOnClass(DataSource.class)
public class SqlReportQueryExecutor {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SqlReportQueryExecutor(DataSource dataSource) {
        this.jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
    }

    public SqlReportQueryResult execute(String sql, Map<String, Object> params, int maxRows) {
        SqlReportQueryValidator.validate(sql);
        Map<String, Object> bound = params == null ? Map.of() : new LinkedHashMap<>(params);

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
                            break;
                        }
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= columnCount; i += 1) {
                            String label = meta.getColumnLabel(i);
                            Object value = JdbcUtils.getResultSetValue(rs, i);
                            row.put(label, value == null ? "—" : String.valueOf(value));
                        }
                        rows.add(row);
                    }
                    return new SqlReportQueryResult(fields, rows);
                });
    }

    public record SqlReportQueryResult(List<String> fields, List<Map<String, Object>> rows) {

        public SqlReportQueryResult {
            fields = fields == null ? List.of() : List.copyOf(fields);
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }
}
