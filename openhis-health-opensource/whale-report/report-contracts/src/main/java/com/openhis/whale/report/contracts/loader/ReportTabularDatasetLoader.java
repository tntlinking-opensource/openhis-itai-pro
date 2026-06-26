package com.openhis.whale.report.contracts.loader;

import java.util.Map;

/**
 * C 类报表表格数据 Port（由 health 侧实现，按 {@code ReportCode} 加载行与汇总）。
 *
 * <p>返回 Map 约定键：{@code rows}（List&lt;Map&gt;）、{@code summary}（Map，可选）。</p>
 */
public interface ReportTabularDatasetLoader {

    /**
     * 加载报表表格数据；不支持该 {@code ReportCode} 时返回空 Map。
     *
     * @param reportCode 报表编码
     * @param params     筛选参数（dateFrom、dateTo、keyword 等）
     * @return 含 {@code rows}、可选 {@code summary} 的 root Map
     */
    Map<String, Object> load(String reportCode, Map<String, Object> params);
}
