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
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;

/**
 * 患者警示聚合根（DM-EMG-005），表 {@code emg_patient_alert}。
 */
@Getter
@Setter
@Entity
@Table(name = "emg_patient_alert")
public class PatientAlert extends BaseAuditedEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REVOKED = "REVOKED";

    public static final String TYPE_BLACKLIST = "BLACKLIST";
    public static final String TYPE_WARNING = "WARNING";
    public static final String TYPE_INFO = "INFO";

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "alert_type_enum", nullable = false)
    private String alertTypeEnum;

    @Column(name = "mark_type_code")
    private String markTypeCode;

    @Column(name = "reason_text", nullable = false)
    private String reasonText;

    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    @Column(name = "effective_from")
    private LocalDateTime effectiveFrom;

    @Column(name = "effective_to")
    private LocalDateTime effectiveTo;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_by")
    private Long revokedBy;

    public void assertReasonTextPresent() {
        if (StringUtils.isBlank(reasonText)) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "警示原因不能为空");
        }
    }

    public void assertEffectiveRangeWhenBothPresent() {
        if (effectiveFrom != null && effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "失效时间须晚于生效时间");
        }
    }

    public void assertRevocableWhenRevoke() {
        if (!STATUS_ACTIVE.equals(statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "仅生效中的警示可撤销");
        }
    }

    public void markRevoked(Long revokedByUser, LocalDateTime revokedAtTime) {
        this.statusEnum = STATUS_REVOKED;
        this.revokedBy = revokedByUser;
        this.revokedAt = revokedAtTime;
    }

    public boolean isEffectiveAt(LocalDateTime asOf) {
        LocalDateTime point = asOf != null ? asOf : LocalDateTime.now();
        if (!STATUS_ACTIVE.equals(statusEnum)) {
            return false;
        }
        if (effectiveFrom != null && point.isBefore(effectiveFrom)) {
            return false;
        }
        return effectiveTo == null || !point.isAfter(effectiveTo);
    }
}
