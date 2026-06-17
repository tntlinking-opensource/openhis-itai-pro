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

/**
 * 打印事件明细。表 {@code rpt_print_event_log}。
 */
@Getter
@Setter
@Entity
@Table(name = "rpt_print_event_log")
public class PrintEventLog {

    @Id
    @GeneratedValue(generator = "snowflake")
    @GenericGenerator(name = "snowflake", type = SnowflakeIdGenerator.class)
    @SuppressWarnings("deprecation")
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "target_type", nullable = false, length = 32)
    private String targetType;

    @Column(name = "target_code", nullable = false, length = 64)
    private String targetCode;

    @Column(name = "template_version")
    private Integer templateVersion;

    @Column(name = "dataset_code", length = 64)
    private String datasetCode;

    @Column(name = "biz_ref_json", columnDefinition = "TEXT")
    private String bizRefJson;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "operator_name", length = 64)
    private String operatorName;

    @Column(name = "organization_id")
    private Long organizationId;

    @Column(name = "copies", nullable = false)
    private Integer copies = 0;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "content_type", length = 64)
    private String contentType;

    @Column(name = "printer_name", length = 128)
    private String printerName;

    @Column(name = "client_job_id", length = 64)
    private String clientJobId;

    @Column(name = "win_duration_ms")
    private Integer winDurationMs;

    @Column(name = "render_duration_ms")
    private Integer renderDurationMs;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "user_agent", length = 256)
    private String userAgent;

    @Column(name = "source", nullable = false, length = 32)
    private String source = "WEB";

    @Column(name = "parent_event_id")
    private Long parentEventId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
