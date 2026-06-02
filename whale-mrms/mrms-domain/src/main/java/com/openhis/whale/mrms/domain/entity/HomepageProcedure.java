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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 病案首页手术操作行。表 mrms_homepage_procedure。
 */
@Getter
@Setter
@Entity
@Table(name = "mrms_homepage_procedure")
public class HomepageProcedure {

    @Id
    @GeneratedValue(generator = "snowflake")
    @GenericGenerator(name = "snowflake", type = SnowflakeIdGenerator.class)
    @SuppressWarnings("deprecation")
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "case_record_id", nullable = false)
    private Long caseRecordId;

    @Column(name = "sort_no", nullable = false)
    private Integer sortNo = 0;

    @Column(name = "oprn_oprt_code", length = 32)
    private String oprnOprtCode;

    @Column(name = "oprn_oprt_name", length = 500)
    private String oprnOprtName;

    @Column(name = "oprn_date")
    private LocalDate oprnDate;

    @Column(name = "oprn_level_code", length = 16)
    private String oprnLevelCode;

    @Column(name = "oprn_incision_code", length = 16)
    private String oprnIncisionCode;

    @Column(name = "heal_grade_code", length = 16)
    private String healGradeCode;

    @Column(name = "anst_way_code", length = 16)
    private String anstWayCode;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
