package com.openhis.whale.report.contracts.constants;

/**
 * C 类报表数据集与 SQL 查询行数上限。
 *
 * <p>{@link #MAX_TABULAR_ROWS}：adapter Port / DatasetService 拉取上限；
 * {@link #MAX_SQL_ROWS}：SQL 执行与 Excel 导出全量上限。</p>
 */
public final class ReportDatasetLimits {

    /** Port 拉取行数上限，超出时 summary 标记 truncated。 */
    public static final int MAX_TABULAR_ROWS = 500;

    /** SQL 报表单次执行内存行数上限（{@code ReportQueryService}）。 */
    public static final int MAX_SQL_ROWS = 5000;

    private ReportDatasetLimits() {
    }
}
