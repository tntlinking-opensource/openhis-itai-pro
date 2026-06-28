/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.mrms.qc.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 质控缺陷实例（{@code mrms_qc_defect}），规则执行后 upsert。
 */
@Getter
@Setter
@Entity
@Table(name = "mrms_qc_defect")
public class QcDefect extends BaseAuditedEntity {

    /** 关联病案主档 {@code mrms_case_record.id}。 */
    @Column(name = "case_record_id", nullable = false)
    private Long caseRecordId;

    /** 触发规则 code（{@code mrms_qc_rule.rule_code}）。 */
    @Column(name = "rule_code", length = 64)
    private String ruleCode;

    /** 缺陷描述（展示给整改人，规则命中或手工补录）。 */
    @Column(name = "defect_message", nullable = false, length = 1000)
    private String defectMessage;

    /** 严重级别 code（{@link com.openhis.health.domain.share.enums.QcDefectSeverity}，block 阻断归档）。 */
    @Column(name = "severity_code", nullable = false, length = 16)
    private String severityCode;

    /** 缺陷状态 code（open/rectified/closed）。 */
    @Column(name = "status_code", nullable = false, length = 16)
    private String statusCode;

    /** 整改责任人 {@code adm_practitioner.id}。 */
    @Column(name = "responsible_practitioner_id")
    private Long responsiblePractitionerId;

    /** 整改完成时间（医生标记 rectified 时写入）。 */
    @Column(name = "rectified_at")
    private LocalDateTime rectifiedAt;

    /** 关闭时间（病案室确认缺陷已收口，status=closed）。 */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;
}
