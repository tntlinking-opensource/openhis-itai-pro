package com.openhis.whale.report.domain.entity;

import com.openhis.whale.core.infrastructure.id.SnowflakeIdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "rpt_definition")
public class ReportDefinition {

    @Id
    @GeneratedValue(generator = "snowflake")
    @GenericGenerator(name = "snowflake", type = SnowflakeIdGenerator.class)
    @SuppressWarnings("deprecation")
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "report_code", nullable = false, length = 64)
    private String reportCode;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "loader_bean", nullable = false, length = 128)
    private String loaderBean;

    @Column(name = "default_columns_json", columnDefinition = "TEXT")
    private String defaultColumnsJson;

    @Column(name = "allow_column_customize", nullable = false)
    private Boolean allowColumnCustomize = true;

    @Column(name = "export_pdf_enabled", nullable = false)
    private Boolean exportPdfEnabled = false;

    @Column(name = "pdf_template_code", length = 64)
    private String pdfTemplateCode;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "source_type", nullable = false, length = 16)
    private String sourceType = "loader";

    @Column(name = "query_sql", columnDefinition = "TEXT")
    private String querySql;

    @Column(name = "header_json", columnDefinition = "TEXT")
    private String headerJson;

    @Column(name = "footer_json", columnDefinition = "TEXT")
    private String footerJson;

    @Column(name = "filter_json", columnDefinition = "TEXT")
    private String filterJson;

    @Column(name = "remark", length = 512)
    private String remark;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;
}
