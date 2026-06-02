/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.emergency.emgtriage.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 分诊评估子实体 {@code emg_triage_assessment}（SDD §4.3）。
 */
@Getter
@Setter
@Entity
@Table(name = "emg_triage_assessment")
public class EmgTriageAssessment extends BaseAuditedEntity {

    @Column(name = "emg_triage_id", nullable = false)
    private Long emgTriageId;

    @Column(name = "assessment_type", nullable = false)
    private String assessmentType;

    @Column(name = "assessment_data", nullable = false, columnDefinition = "text")
    private String assessmentData;

    @Column(name = "assessment_score", precision = 16, scale = 4)
    private BigDecimal assessmentScore;

    @Column(name = "assessed_at", nullable = false)
    private LocalDateTime assessedAt;

    @Column(name = "assessed_by")
    private Long assessedBy;

    public void markDeleted(Long operatorId) {
        this.setIsDeleted(Boolean.TRUE);
        this.setDeletedBy(operatorId);
        this.setDeletedAt(LocalDateTime.now());
    }
}
