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

package com.openhis.health.domain.clinical.allergyintolerance.service;

import com.openhis.health.domain.clinical.allergyintolerance.command.DeleteSkinTestRecordCommand;
import com.openhis.health.domain.clinical.allergyintolerance.command.SaveSkinTestRecordCommand;
import com.openhis.health.domain.clinical.allergyintolerance.entity.AllergyIntolerance;
import com.openhis.health.domain.clinical.allergyintolerance.repository.AllergyIntoleranceRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.events.clinical.allergyintolerance.AllergyIntoleranceRecordedEvent;
import com.openhis.health.domain.share.events.clinical.allergyintolerance.AllergyIntoleranceRemovedEvent;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 过敏记录写侧领域服务。
 */
@Service
@RequiredArgsConstructor
public class AllergyIntoleranceDomainService {
    private final AllergyIntoleranceRepository repository;
    private final IDomainEventPublisher domainEventPublisher;

    /**
     * 保存皮试记录。
     *
     * @param cmd 保存命令
     * @return 记录标识
     * @preconditions requestId/encounterId/patientId 与 clinicalStatusEnum/verificationStatusEnum 必填
     * @throws BusinessException 参数缺失或更新目标不存在时抛出
     */
    public Long saveSkinTestRecord(SaveSkinTestRecordCommand cmd) {
        AllergyIntolerance entity;
        // 更新路径必须基于既有记录，禁止“传 id 但查不到时静默新建”。
        if (cmd.getId() == null) {
            entity = new AllergyIntolerance();
        } else {
            entity = repository.findById(cmd.getId()).orElseThrow(
                () -> new BusinessException(ErrorCodeConstants.ALLERGY_INTOLERANCE_NOT_FOUND, "过敏记录不存在"));
        }
        // Pre：同步命令字段到聚合根，供后续统一走聚合不变式校验。
        entity.setRequestId(cmd.getRequestId());
        entity.setEncounterId(cmd.getEncounterId());
        entity.setPatientId(cmd.getPatientId());
        entity.setMedicationId(cmd.getMedicationId());
        entity.setLotNumber(cmd.getLotNumber());
        entity.setClinicalStatusEnum(cmd.getClinicalStatusEnum());
        entity.setVerificationStatusEnum(cmd.getVerificationStatusEnum());
        entity.setTypeEnum(cmd.getTypeEnum());
        entity.setCategoryCode(cmd.getCategoryCode());
        entity.setCriticalityEnum(cmd.getCriticalityEnum());
        entity.setCode(cmd.getCode());
        entity.setDescription(cmd.getDescription());
        entity.setSeverityEnum(cmd.getSeverityEnum());
        entity.setOnsetDateTime(cmd.getOnsetDateTime());
        entity.setRecordedDate(cmd.getRecordedDate());
        entity.setLastReactionOccurrence(cmd.getLastReactionOccurrence());
        entity.setExposureRoute(cmd.getExposureRoute());
        entity.setPractitionerId(cmd.getPractitionerId());
        entity.setCheckPractitionerId(cmd.getCheckPractitionerId());
        entity.setNote(cmd.getNote());
        // Assert：BR-ALI-001 保存记录必须绑定申请、就诊、患者。
        entity.assertRequiredReferences();
        // Assert：BR-ALI-002 临床状态与验证状态必须成对记录。
        entity.assertStatusPairPresent();
        // Assert：BR-ALI-004 人员引用填写时必须满足可追溯的有效标识约束。
        entity.assertPractitionerReference();
        // 先落库再发布事件，避免“先发事件后失败”导致幽灵事件。
        AllergyIntolerance saved = repository.save(entity);
        domainEventPublisher.publish(new AllergyIntoleranceRecordedEvent(saved.getId(), saved.getEncounterId(), saved.getPatientId(),
            saved.getMedicationId(), saved.getClinicalStatusEnum(), saved.getVerificationStatusEnum()));
        return saved.getId();
    }

    /**
     * 删除皮试记录。
     *
     * @param cmd 删除命令
     * @throws BusinessException 记录不存在或入参非法时抛出
     */
    public void deleteSkinTestRecord(DeleteSkinTestRecordCommand cmd) {
        // Assert：BR-ALI-003 删除必须基于记录 ID。
        if (cmd.getRecordId() == null) {
            throw new BusinessException(ErrorCodeConstants.ALLERGY_INTOLERANCE_INVALID_ARGUMENT, "记录标识不能为空");
        }
        AllergyIntolerance entity = repository.findById(cmd.getRecordId()).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.ALLERGY_INTOLERANCE_NOT_FOUND, "过敏记录不存在"));
        entity.assertDeletable();
        entity.markDeleted(cmd.getOperatorId());
        // 先保存逻辑删除，再发布“已删除”事件。
        repository.save(entity);
        domainEventPublisher.publish(new AllergyIntoleranceRemovedEvent(entity.getId(), entity.getEncounterId(), entity.getPatientId()));
    }
}
