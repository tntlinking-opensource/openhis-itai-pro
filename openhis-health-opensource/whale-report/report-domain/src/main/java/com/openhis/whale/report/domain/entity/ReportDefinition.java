package com.openhis.whale.report.domain.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import com.openhis.whale.core.domain.IMultiTenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * C 类报表定义。表 {@code rpt_definition}。
 */
@Getter
@Setter
@Entity
@Table(name = "rpt_definition")
public class ReportDefinition extends BaseAuditedEntity implements IMultiTenant {

    /** 报表编码，租户内唯一。 */
    @Column(name = "report_code", nullable = false, length = 64)
    private String reportCode;

    /** 展示名称。 */
    @Column(name = "name", nullable = false, length = 128)
    private String name;

    /** Spring Bean 名，Loader 路径取数用（如 {@code cashierDailyLoader}）。 */
    @Column(name = "loader_bean", nullable = false, length = 128)
    private String loaderBean;

    /** 默认列 JSON，与 {@code ReportColumnDto} 列表同构。 */
    @Column(name = "default_columns_json", columnDefinition = "TEXT")
    private String defaultColumnsJson;

    /** 是否允许用户列定制。 */
    @Column(name = "allow_column_customize", nullable = false)
    private Boolean allowColumnCustomize = true;

    /** 是否允许导出 PDF。 */
    @Column(name = "export_pdf_enabled", nullable = false)
    private Boolean exportPdfEnabled = false;

    /** PDF 打印模板编码。 */
    @Column(name = "pdf_print_code", length = 64)
    private String pdfPrintCode;

    /** 发布状态：{@code draft} / {@code published}。 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 数据来源：{@code loader} / {@code sql}。 */
    @Column(name = "source_type", nullable = false, length = 16)
    private String sourceType = "loader";

    /** SQL 报表查询语句（loader 类型通常为空）。 */
    @Column(name = "query_sql", columnDefinition = "TEXT")
    private String querySql;

    /** 表头设计 JSON（设计器存储真源）。 */
    @Column(name = "header_json", columnDefinition = "TEXT")
    private String headerJson;

    /** 表底设计 JSON。 */
    @Column(name = "footer_json", columnDefinition = "TEXT")
    private String footerJson;

    /** 筛选器定义 JSON。 */
    @Column(name = "filter_json", columnDefinition = "TEXT")
    private String filterJson;

    /** 备注。 */
    @Column(name = "remark", length = 512)
    private String remark;
}
