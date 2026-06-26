package com.openhis.whale.report.application.report.support;

import com.openhis.whale.report.application.report.response.ReportColumnDto;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.report.application.report.loader.warehouse.SupplyDocumentReportLoader;
import com.openhis.whale.report.contracts.catalog.supply.SupplyDocumentReportCatalog;
import com.openhis.whale.report.contracts.catalog.supply.SupplyDocumentReportSpec;
import org.springframework.stereotype.Component;
import com.openhis.whale.core.application.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * C 类 Loader 注册表。
 *
 * <p>启动时收集所有 Spring {@link ReportLoader} Bean，并追加
 * {@link SupplyDocumentReportCatalog} 中的出入库单据 Loader（无需单独写类）。</p>
 *
 * <p>与 {@code rpt_definition.loader_bean} 无关：Loader 按 {@code ReportCode} 索引，
 * 每个 Loader 内部通过 {@link ReportLoaderDataSupport} 调 {@code ReportTabularDatasetLoader}。</p>
 */
@Component
public class ReportLoaderRegistry {

    private final Map<String, ReportLoader> loaderByCode;

    public ReportLoaderRegistry(List<ReportLoader> loaders, ReportLoaderDataSupport dataSupport) {
        Map<String, ReportLoader> map = new LinkedHashMap<>();
        if (loaders != null) {
            for (ReportLoader loader : loaders) {
                if (loader == null || !StringUtils.isNotEmpty(loader.reportCode())) {
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

    /** 按 ReportCode 取 Loader；未注册时抛 ValidationException。
     *
     * @param reportCode 报表编码
     * @return 已注册的 Loader 实例
     */
    public ReportLoader require(String reportCode) {
        String code = "";
        if (reportCode != null) {
            code = reportCode.trim();
        }
        if (!StringUtils.isNotEmpty(code)) {
            throw new ValidationException("报表编码不能为空");
        }
        ReportLoader loader = loaderByCode.get(code);
        if (loader == null) {
            // 未在 Spring Bean 与 SupplyDocument 目录中注册
            throw new ValidationException("未注册的报表编码: " + code);
        }
        return loader;
    }

    /**
     * 全部已注册 Loader，按名称排序。
     */
    public List<ReportLoader> listAll() {
        List<ReportLoader> loaders = new ArrayList<>(loaderByCode.values());
        loaders.sort(Comparator.comparing(ReportLoader::reportName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(loaders);
    }

    /**
     * 只读索引：ReportCode → Loader。
     *
     * @return 不可变 Map
     */
    public Map<String, ReportLoader> indexByCode() {
        return loaderByCode;
    }

    /**
     * 按编码查找 Loader（不存在返回 empty）。
     *
     * @param reportCode 报表编码
     * @return 已注册 Loader，不存在时 empty
     */
    public Optional<ReportLoader> find(String reportCode) {
        if (!StringUtils.isNotEmpty(reportCode)) {
            return Optional.empty();
        }
        return Optional.ofNullable(loaderByCode.get(reportCode.trim()));
    }

    /**
     * 将列 DTO 列表索引为 field → DTO（供 merge 时回填 label/align）。
     *
     * @param columns 列列表
     * @return field → 列 DTO 的有序 Map
     */
    public static Map<String, ReportColumnDto> indexColumns(List<ReportColumnDto> columns) {
        Map<String, ReportColumnDto> map = new LinkedHashMap<>();
        if (columns == null) {
            return map;
        }
        for (ReportColumnDto column : columns) {
            if (column == null || !StringUtils.isNotEmpty(column.getField())) {
                continue;
            }
            map.put(column.getField().trim(), column);
        }
        return map;
    }
}
