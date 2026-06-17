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
@Table(name = "rpt_template")
public class ReportTemplate {

    @Id
    @GeneratedValue(generator = "snowflake")
    @GenericGenerator(name = "snowflake", type = SnowflakeIdGenerator.class)
    @SuppressWarnings("deprecation")
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "pipeline", nullable = false, length = 16)
    private String pipeline;

    @Column(name = "layout_json", columnDefinition = "TEXT")
    private String layoutJson;

    @Column(name = "dataset_code", length = 64)
    private String datasetCode;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "organization_id")
    private Long organizationId;

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
