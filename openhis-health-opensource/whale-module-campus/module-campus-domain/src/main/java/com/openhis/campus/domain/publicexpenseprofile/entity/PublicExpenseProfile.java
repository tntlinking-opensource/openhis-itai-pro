/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.publicexpenseprofile.entity;

import com.openhis.campus.domain.shared.CampusDomainConstants;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 公费资格档案（cmp_pe_profile）。
 */
@Getter
@Setter
@Entity
@Table(name = "cmp_pe_profile")
public class PublicExpenseProfile extends BaseAuditedEntity {

    @Column(name = "org_id")
    private Long orgId;

    @Column(name = "subject_type_enum", nullable = false, length = 32)
    private String subjectTypeEnum;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "card_no", length = 64)
    private String cardNo;

    @Column(name = "medical_code", length = 64)
    private String medicalCode;

    @Column(name = "gf_status_enum", nullable = false, length = 32)
    private String gfStatusEnum = CampusDomainConstants.GF_STATUS_UNKNOWN;

    @Column(name = "category_enum", length = 32)
    private String categoryEnum;

    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    @Column(name = "valid_to")
    private LocalDateTime validTo;

    @Column(name = "eligible_flag", nullable = false)
    private Boolean eligibleFlag = Boolean.TRUE;

    @Column(name = "remark", length = 512)
    private String remark;
}
