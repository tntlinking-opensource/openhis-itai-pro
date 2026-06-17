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
@Table(name = "rpt_print_element_def")
public class PrintElementDef {

    @Id
    @GeneratedValue(generator = "snowflake")
    @GenericGenerator(name = "snowflake", type = SnowflakeIdGenerator.class)
    @SuppressWarnings("deprecation")
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "pack_code", nullable = false, length = 64)
    private String packCode;

    @Column(name = "element_code", nullable = false, length = 64)
    private String elementCode;

    @Column(name = "label", nullable = false, length = 128)
    private String label;

    @Column(name = "render_type", nullable = false, length = 32)
    private String renderType;

    @Column(name = "bind_path", length = 256)
    private String bindPath;

    @Column(name = "kv_items_json", columnDefinition = "TEXT")
    private String kvItemsJson;

    @Column(name = "column_defs_json", columnDefinition = "TEXT")
    private String columnDefsJson;

    @Column(name = "default_style_json", columnDefinition = "TEXT")
    private String defaultStyleJson;

    @Column(name = "default_label", length = 256)
    private String defaultLabel;

    @Column(name = "box_json", columnDefinition = "TEXT")
    private String boxJson;

    @Column(name = "layout_mode", nullable = false, length = 16)
    private String layoutMode = "flow";

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "required", nullable = false)
    private Boolean required = false;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
