/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.healtharchive.entity;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 体检健康档案（peis_patient_health_archive）。
 */
@Getter
@Setter
@Entity
@Table(name = "peis_patient_health_archive")
public class PeisPatientHealthArchive extends BaseAuditedEntity {

    @Column(name = "org_id")
    private Long orgId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "past_history")
    private String pastHistory;

    @Column(name = "family_history")
    private String familyHistory;

    @Column(name = "habit_summary")
    private String habitSummary;

    @Column(name = "allergy_summary")
    private String allergySummary;

    /** 体检标签编码。 */
    @Column(name = "tag_code")
    private String tagCode;

    @Column(name = "remark")
    private String remark;
}
