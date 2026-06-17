package com.openhis.whale.report.application.query.support;

import com.openhis.whale.report.application.query.response.ReportColumnDto;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.report.application.query.loader.warehouse.SupplyDocumentReportLoader;
import com.openhis.whale.report.contracts.supply.SupplyDocumentReportCatalog;
import com.openhis.whale.report.contracts.supply.SupplyDocumentReportSpec;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ReportLoaderRegistry {

    private final Map<String, ReportLoader> loaderByCode;

    public ReportLoaderRegistry(List<ReportLoader> loaders, ReportLoaderDataSupport dataSupport) {
        Map<String, ReportLoader> map = new LinkedHashMap<>();
        if (loaders != null) {
            for (ReportLoader loader : loaders) {
                if (loader == null || !StringUtils.hasText(loader.reportCode())) {
                    continue;
                }
                map.put(loader.reportCode().trim(), loader);
            }
        }
        for (SupplyDocumentReportSpec spec : SupplyDocumentReportCatalog.sorted()) {
            map.putIfAbsent(spec.reportCode(), new SupplyDocumentReportLoader(spec, dataSupport));
        }
        this.loaderByCode = Map.copyOf(map);
    }

    public ReportLoader require(String reportCode) {
        String code = "";
        if (reportCode != null) {
            code = reportCode.trim();
        }
        if (!StringUtils.hasText(code)) {
            throw new ValidationException("报表编码不能为空");
        }
        ReportLoader loader = loaderByCode.get(code);
        if (loader == null) {
            throw new ValidationException("未注册的报表编码: " + code);
        }
        return loader;
    }

    public List<ReportLoader> listAll() {
        List<ReportLoader> loaders = new ArrayList<>(loaderByCode.values());
        loaders.sort(Comparator.comparing(ReportLoader::reportName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(loaders);
    }

    public Map<String, ReportLoader> indexByCode() {
        return loaderByCode;
    }

    public Optional<ReportLoader> find(String reportCode) {
        if (!StringUtils.hasText(reportCode)) {
            return Optional.empty();
        }
        return Optional.ofNullable(loaderByCode.get(reportCode.trim()));
    }

    public static Map<String, ReportColumnDto> indexColumns(List<ReportColumnDto> columns) {
        Map<String, ReportColumnDto> map = new LinkedHashMap<>();
        if (columns == null) {
            return map;
        }
        for (ReportColumnDto column : columns) {
            if (column == null || !StringUtils.hasText(column.getField())) {
                continue;
            }
            map.put(column.getField().trim(), column);
        }
        return map;
    }
}
