package com.openhis.whale.report.domain.port.tabular;

import java.util.Map;

/**
 * C 类报表表格数据 Port（由 health 侧实现，按 {@code reportCode} 加载行与汇总）。
 *
 * <p>返回 Map 约定键：{@code rows}（List&lt;Map&gt;）、{@code summary}（Map，可选）。</p>
 */
public interface ReportTabularDataPort {

    /**
     * 加载报表表格数据；不支持该 {@code reportCode} 时返回空 Map。
     */
    Map<String, Object> load(String reportCode, Map<String, Object> params);
}
