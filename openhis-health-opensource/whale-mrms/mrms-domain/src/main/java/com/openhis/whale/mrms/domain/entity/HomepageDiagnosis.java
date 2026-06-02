package com.openhis.whale.mrms.domain.entity;

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
 * 病案首页诊断行。表 mrms_homepage_diagnosis。
 */
@Getter
@Setter
@Entity
@Table(name = "mrms_homepage_diagnosis")
public class HomepageDiagnosis {

    @Id
    @GeneratedValue(generator = "snowflake")
    @GenericGenerator(name = "snowflake", type = SnowflakeIdGenerator.class)
    @SuppressWarnings("deprecation")
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "case_record_id", nullable = false)
    private Long caseRecordId;

    @Column(name = "diag_type_code", nullable = false, length = 32)
    private String diagTypeCode;

    @Column(name = "sort_no", nullable = false)
    private Integer sortNo = 0;

    @Column(name = "diag_code", length = 32)
    private String diagCode;

    @Column(name = "diag_name", length = 500)
    private String diagName;

    @Column(name = "adm_cond_code", length = 16)
    private String admCondCode;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
