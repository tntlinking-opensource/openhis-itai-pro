package com.openhis.whale.report.application.query.support;

import com.openhis.whale.report.application.query.response.ReportQueryPageResultDto;
import com.openhis.whale.report.application.query.request.ReportQueryPageRequest;
import com.openhis.whale.report.domain.port.tabular.ReportTabularDataPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ReportLoaderDataSupport {

    private final ReportTabularDataPort tabularDataPort;

    public ReportLoaderDataSupport(@Autowired(required = false) ReportTabularDataPort tabularDataPort) {
        this.tabularDataPort = tabularDataPort;
    }

    public boolean applyPortDataIfPresent(
            String reportCode,
            ReportQueryPageRequest request,
            ReportQueryPageResultDto result) {
        if (tabularDataPort == null || result == null) {
            return false;
        }
        Map<String, Object> params = buildParams(request);
        Map<String, Object> loaded = tabularDataPort.load(reportCode, params);
        if (loaded == null || loaded.isEmpty()) {
            return false;
        }
        List<Map<String, Object>> rows = castRowList(loaded.get("rows"));
        if (rows == null) {
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
