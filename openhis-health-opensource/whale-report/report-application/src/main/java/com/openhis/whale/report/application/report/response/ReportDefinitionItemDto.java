package com.openhis.whale.report.application.report.response;

import lombok.Data;

import java.util.List;

/**
 * 报表定义详情（设计器/中心列表用）。
 *
 * <p>表头/表底/筛选器以结构化字段出参；保存设计仍通过 {@code saveReportDesign} 提交 JSON 入参写库。</p>
 */
@Data
public class ReportDefinitionItemDto {

    /** 定义主键 {@code rpt_definition.id}。 */
    private Long id;
    /** 报表编码，租户内唯一。 */
    private String reportCode;
    /** 展示名称。 */
    private String name;
    /** 发布状态：{@code draft} 草稿 / {@code published} 已发布。 */
    private String status;
    /** 是否允许导出 PDF。 */
    private Boolean exportPdfEnabled;
    /** PDF 打印模板编码（绑定 Print 模块 printCode；exportPdfEnabled 为 true 时必填）。 */
    private String pdfTemplateCode;
    /** 是否允许用户列定制。 */
    private Boolean allowColumnCustomize;
    /** 默认列（含 Loader 回填的 label/align/width）。 */
    private List<ReportColumnDto> columns;

    /** 数据来源：{@code loader} 出厂 / {@code sql} 设计器 SQL。 */
    private String sourceType;
    /** SQL 报表查询语句（loader 类型为空）。 */
    private String querySql;
    /** 表头设计（结构化；queryPage 出参另见 {@link ReportQueryPageResultDto#getHeader()}）。 */
    private ReportHeaderConfigDto header;
    /** 表底设计（结构化）。 */
    private ReportFooterConfigDto footer;
    /** 筛选器字段列表（报表中心动态表单）。 */
    private List<ReportFilterFieldDto> filters;
    /** 备注。 */
    private String remark;
}
