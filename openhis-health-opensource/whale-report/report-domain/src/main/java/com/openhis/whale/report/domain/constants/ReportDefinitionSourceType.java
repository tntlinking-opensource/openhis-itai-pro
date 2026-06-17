package com.openhis.whale.report.domain.constants;

/**
 * C 类报表数据来源类型。
 */
public final class ReportDefinitionSourceType {

    /** 内置 ReportLoader（出厂/Java 扩展） */
    public static final String LOADER = "loader";

    /** 设计器维护的只读 SELECT SQL */
    public static final String SQL = "sql";

    private ReportDefinitionSourceType() {}
}
