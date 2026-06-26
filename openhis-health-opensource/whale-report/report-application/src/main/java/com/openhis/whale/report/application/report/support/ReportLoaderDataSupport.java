package com.openhis.whale.report.application.report.support;

import com.openhis.whale.report.application.report.response.ReportQueryPageResultDto;
import com.openhis.whale.report.application.report.request.ReportQueryPageRequest;
import com.openhis.whale.report.contracts.loader.ReportTabularDatasetLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loader 取数桥接：将 {@link ReportQueryPageRequest} 转为 Port 参数并写回分页结果。
 *
 * <p>adapter 未装配 {@link ReportTabularDatasetLoader} 时返回 false，由 Loader 样例数据兜底。</p>
 */
@Component
public class ReportLoaderDataSupport {

    private final ReportTabularDatasetLoader tabularDatasetLoader;

    public ReportLoaderDataSupport(@Autowired(required = false) ReportTabularDatasetLoader tabularDatasetLoader) {
        this.tabularDatasetLoader = tabularDatasetLoader;
    }

    /**
     * 若 Port 有数据则填充 result 并返回 true；否则由调用方走 fallback。
     *
     * @param reportCode 报表编码
     * @param request    分页与筛选
     * @param result     待填充的结果对象（列须已设置）
     * @return Port 是否成功提供行数据
     */
    public boolean applyPortDataIfPresent(
            String reportCode,
            ReportQueryPageRequest request,
            ReportQueryPageResultDto result) {
        // adapter 未引入或 result 未初始化时走 Loader 样例
        if (tabularDatasetLoader == null || result == null) {
            return false;
        }
        Map<String, Object> params = buildParams(request);
        Map<String, Object> loaded = tabularDatasetLoader.load(reportCode, params);
        if (loaded == null || loaded.isEmpty()) {
            // Port 未返回数据集
            return false;
        }
        List<Map<String, Object>> rows = castRowList(loaded.get("rows"));
        if (rows == null) {
            // root 缺少 rows 键或类型不符
            return false;
        }
        int pageNo = Math.max(request.getPageNo(), 1);
        int pageSize = Math.max(request.getPageSize(), 1);
        int from = (pageNo - 1) * pageSize;
        int to = Math.min(from + pageSize, rows.size());
        List<Map<String, Object>> pageRows = ReportQueryPaging.slice(rows, from, to);

        result.setRows(pageRows);
        result.setTotalCount(rows.size());
        result.setPageNo(pageNo);
        result.setPageSize(pageSize);

        Object summary = loaded.get("summary");
        if (summary instanceof Map<?, ?> summaryMap) {
            result.setSummary(castStringKeyMap(summaryMap));
        }
        return true;
    }

    private static Map<String, Object> buildParams(ReportQueryPageRequest request) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (request.getDateFrom() != null) {
            params.put("dateFrom", request.getDateFrom().trim());
        }
        if (request.getDateTo() != null) {
            params.put("dateTo", request.getDateTo().trim());
        }
        if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
            params.put("keyword", request.getKeyword().trim());
        }
        if (request.getParams() != null) {
            params.putAll(request.getParams());
        }
        return params;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castRowList(Object raw) {
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    rows.add(castStringKeyMap(map));
                }
            }
            return rows;
        }
        return null;
    }

    private static Map<String, Object> castStringKeyMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }
}
