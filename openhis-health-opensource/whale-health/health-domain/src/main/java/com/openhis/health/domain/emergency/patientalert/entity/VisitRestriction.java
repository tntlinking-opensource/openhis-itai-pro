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

package com.openhis.health.domain.emergency.patientalert.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 就诊限制聚合根（DM-EMG-005），表 {@code emg_visit_restriction}。
 */
@Getter
@Setter
@Entity
@Table(name = "emg_visit_restriction")
public class VisitRestriction extends BaseAuditedEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_LIFTED = "LIFTED";

    public static final String TYPE_GENERAL = "GENERAL";
    public static final String TYPE_FLOW_CONTROL = "FLOW_CONTROL";

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "restriction_type_enum", nullable = false)
    private String restrictionTypeEnum;

    @Column(name = "reason_text")
    private String reasonText;

    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    @Column(name = "lifted_at")
    private LocalDateTime liftedAt;

    @Column(name = "operated_by")
    private Long operatedBy;

    public void assertLiftableWhenLift() {
        if (!STATUS_ACTIVE.equals(statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "仅生效中的就诊限制可解除");
        }
    }

    public void markLifted(LocalDateTime liftedAtTime, Long operatorId) {
        this.statusEnum = STATUS_LIFTED;
        this.liftedAt = liftedAtTime;
        this.operatedBy = operatorId;
    }
}
