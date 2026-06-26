package com.openhis.whale.report.application.report.response;

import lombok.Data;

/** SQL 设计器元数据：单列定义。 */
@Data
public class ReportSqlMetadataColumnDto {

    /** 列字段名或表达式片段。 */
    private String field;
    /** 设计器展示标签。 */
    private String label;
    /** 聚合/筛选角色：{@code count} / {@code sum} / {@code group} / {@code filter}。 */
    private String role;
}
