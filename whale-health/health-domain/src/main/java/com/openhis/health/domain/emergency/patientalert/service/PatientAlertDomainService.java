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

package com.openhis.health.domain.emergency.patientalert.service;

import com.openhis.health.domain.emergency.patientalert.command.CreatePatientAlertCommand;
import com.openhis.health.domain.emergency.patientalert.command.CreateVisitRestrictionCommand;
import com.openhis.health.domain.emergency.patientalert.command.LiftVisitRestrictionCommand;
import com.openhis.health.domain.emergency.patientalert.command.RevokePatientAlertCommand;
import com.openhis.health.domain.emergency.patientalert.entity.PatientAlert;
import com.openhis.health.domain.emergency.patientalert.entity.VisitRestriction;
import com.openhis.health.domain.emergency.patientalert.repository.PatientAlertRepository;
import com.openhis.health.domain.emergency.patientalert.repository.VisitRestrictionRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.events.emergency.patientalert.PatientAlertCreatedEvent;
import com.openhis.health.domain.share.events.emergency.patientalert.PatientAlertRevokedEvent;
import com.openhis.health.domain.share.events.emergency.patientalert.VisitRestrictionCreatedEvent;
import com.openhis.health.domain.share.events.emergency.patientalert.VisitRestrictionLiftedEvent;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 患者警示与就诊限制写侧（DM-EMG-005 §5.1）。
 */
@Service
@RequiredArgsConstructor
public class PatientAlertDomainService {

    private final PatientAlertRepository patientAlertRepository;
    private final VisitRestrictionRepository visitRestrictionRepository;
    private final IDomainEventPublisher domainEventPublisher;

    public Long createPatientAlert(CreatePatientAlertCommand cmd) {
        if (cmd == null || cmd.getPatientId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "患者标识不能为空");
        }
        if (StringUtils.isBlank(cmd.getAlertTypeEnum())) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "警示类型不能为空");
        }
        PatientAlert entity = new PatientAlert();
        entity.setIsDeleted(Boolean.FALSE);
        entity.setPatientId(cmd.getPatientId());
        entity.setAlertTypeEnum(cmd.getAlertTypeEnum().trim());
        entity.setMarkTypeCode(StringUtils.trimToNull(cmd.getMarkTypeCode()));
        entity.setReasonText(StringUtils.trimToNull(cmd.getReasonText()));
        entity.setStatusEnum(PatientAlert.STATUS_ACTIVE);
        entity.setEffectiveFrom(cmd.getEffectiveFrom() != null ? cmd.getEffectiveFrom() : LocalDateTime.now());
        entity.setEffectiveTo(cmd.getEffectiveTo());
        entity.setCreatedBy(cmd.getCreatedBy());
        entity.assertReasonTextPresent();
        entity.assertEffectiveRangeWhenBothPresent();
        PatientAlert saved = patientAlertRepository.save(entity);
        domainEventPublisher.publish(new PatientAlertCreatedEvent(
            saved.getId(), saved.getPatientId(), saved.getAlertTypeEnum()));
        return saved.getId();
    }

    public void revokePatientAlert(RevokePatientAlertCommand cmd) {
        if (cmd == null || cmd.getAlertId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "警示标识不能为空");
        }
        PatientAlert entity = patientAlertRepository.findById(cmd.getAlertId())
            .filter(a -> !Boolean.TRUE.equals(a.getIsDeleted()))
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "警示记录不存在"));
        entity.assertRevocableWhenRevoke();
        LocalDateTime now = LocalDateTime.now();
        entity.markRevoked(cmd.getRevokedBy(), now);
        patientAlertRepository.save(entity);
        domainEventPublisher.publish(new PatientAlertRevokedEvent(
            entity.getId(), entity.getPatientId(), cmd.getRevokedBy(), now));
    }

    public Long createVisitRestriction(CreateVisitRestrictionCommand cmd) {
        if (cmd == null || cmd.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        if (StringUtils.isBlank(cmd.getRestrictionTypeEnum())) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "限制类型不能为空");
        }
        VisitRestriction entity = new VisitRestriction();
        entity.setIsDeleted(Boolean.FALSE);
        entity.setEncounterId(cmd.getEncounterId());
        entity.setRestrictionTypeEnum(cmd.getRestrictionTypeEnum().trim());
        entity.setReasonText(StringUtils.trimToNull(cmd.getReasonText()));
        entity.setStatusEnum(VisitRestriction.STATUS_ACTIVE);
        entity.setOperatedBy(cmd.getOperatedBy());
        VisitRestriction saved = visitRestrictionRepository.save(entity);
        domainEventPublisher.publish(new VisitRestrictionCreatedEvent(
            saved.getId(), saved.getEncounterId(), saved.getRestrictionTypeEnum()));
        return saved.getId();
    }

    public void liftVisitRestriction(LiftVisitRestrictionCommand cmd) {
        if (cmd == null || cmd.getRestrictionId() == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "限制标识不能为空");
        }
        VisitRestriction entity = visitRestrictionRepository.findById(cmd.getRestrictionId())
            .filter(r -> !Boolean.TRUE.equals(r.getIsDeleted()))
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "就诊限制不存在"));
        entity.assertLiftableWhenLift();
        LocalDateTime now = LocalDateTime.now();
        entity.markLifted(now, cmd.getOperatedBy());
        visitRestrictionRepository.save(entity);
        domainEventPublisher.publish(new VisitRestrictionLiftedEvent(entity.getId(), entity.getEncounterId(), now));
    }
}
