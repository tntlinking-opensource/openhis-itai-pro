package com.openhis.whale.report.application.report.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 保存报表设计（SQL、表头表底、筛选器、默认列）。入参 JSON 字段与库表列同构。 */
@Data
public class SaveReportDesignRequest {

    /** 报表定义主键。 */
    @NotNull
    private Long id;

    /** 展示名称（可选更新）。 */
    private String name;
    /** SQL 查询语句（SQL 类型报表）。 */
    private String querySql;
    /** 表头设计 JSON 字符串。 */
    private String headerJson;
    /** 表底设计 JSON 字符串。 */
    private String footerJson;
    /** 筛选器定义 JSON 字符串。 */
    private String filterJson;
    /** 默认列 JSON 字符串。 */
    private String defaultColumnsJson;
    /** 是否启用 PDF 导出。 */
    private Boolean exportPdfEnabled;
    /** PDF 模板编码（与 Print 模块 printCode 一致）。 */
    @JsonAlias("pdfPrintCode")
    private String pdfTemplateCode;
}
