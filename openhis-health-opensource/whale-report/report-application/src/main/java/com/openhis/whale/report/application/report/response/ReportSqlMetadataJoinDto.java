package com.openhis.whale.report.application.report.response;

import lombok.Data;

/** SQL 设计器元数据：表关联定义。 */
@Data
public class ReportSqlMetadataJoinDto {

    /** 关联物理表名。 */
    private String tableName;
    /** JOIN 别名。 */
    private String alias;
    /** 设计器展示名。 */
    private String label;
    /** ON 子句（不含 JOIN 关键字）。 */
    private String onClause;
}
