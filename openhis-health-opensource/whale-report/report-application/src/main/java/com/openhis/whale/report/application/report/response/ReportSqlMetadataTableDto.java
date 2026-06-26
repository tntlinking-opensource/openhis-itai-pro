package com.openhis.whale.report.application.report.response;

import lombok.Data;

import java.util.List;

/** SQL 设计器元数据：单张业务表及其可关联表。 */
@Data
public class ReportSqlMetadataTableDto {

    /** 物理表名。 */
    private String tableName;
    /** 设计器展示名。 */
    private String label;
    /** 业务说明。 */
    private String description;
    /** 默认 SQL 别名。 */
    private String defaultAlias;
    /** 默认日期筛选列（用于 dateFrom/dateTo 提示）。 */
    private String dateColumn;
    /** 可选列与聚合角色。 */
    private List<ReportSqlMetadataColumnDto> columns;
    /** 可 JOIN 的关联表。 */
    private List<ReportSqlMetadataJoinDto> joins;
}
