/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.allergyintolerance.service;

import com.zxplat.domain.clinical.allergyintolerance.command.DeleteSkinTestRecordCommand;
import com.zxplat.domain.clinical.allergyintolerance.command.SaveDeclaredAllergyRecordCommand;
import com.zxplat.domain.clinical.allergyintolerance.command.SaveNkdaCommand;
import com.zxplat.domain.clinical.allergyintolerance.command.SaveSkinTestRecordCommand;
import com.zxplat.domain.clinical.allergyintolerance.entity.AllergyIntolerance;
import com.zxplat.domain.clinical.allergyintolerance.repository.AllergyIntoleranceRepository;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.enums.AllergyRecordType;
import com.zxplat.domain.share.enums.AllergyVerificationStatus;
import com.zxplat.domain.share.enums.ClinicalStatus;
import com.zxplat.domain.share.events.clinical.allergyintolerance.AllergyIntoleranceRecordedEvent;
import com.zxplat.domain.share.events.clinical.allergyintolerance.AllergyIntoleranceRemovedEvent;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.domain.event.IDomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.zxplat.framework.core.application.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 过敏/皮试记录（{@code cln_allergy_intolerance}）聚合写侧领域服务。
 *
 * <p>职责：皮试保存/删除、主诉过敏史与无已知过敏登记；引用与状态成对校验委托聚合根。</p>
 * <p>不变量：BR-ALI-001～004；皮试阳性须作废该患者 NKDA；持久化先于事件发布。</p>
 * <p>仅注入本聚合 {@link AllergyIntoleranceRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class AllergyIntoleranceDomainService {
    private final AllergyIntoleranceRepository repository;
    private final IDomainEventPublisher domainEventPublisher;

    /**
     * 保存皮试记录。阳性结果计入患者过敏史，并作废该患者「无已知过敏」。
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
        entity.setTypeEnum(AllergyRecordType.SKIN_TEST.getCode());
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
        // 前置校验：皮试必须绑申请、就诊、患者
        entity.assertRequiredReferences();
        // Assert：BR-ALI-002 临床状态与验证状态必须成对记录。
        entity.assertStatusPairPresent();
        // Assert：BR-ALI-004 人员引用填写时必须满足可追溯的有效标识约束。
        entity.assertPractitionerReference();
        // 持久化
        AllergyIntolerance saved = repository.save(entity);
        // 阳性皮试进入患者过敏史：作废 NKDA，避免「无过敏」与阳性并存
        if (ClinicalStatus.ACTIVE.getCode().equals(saved.getClinicalStatusEnum())) {
            this.inactivateNkdaForPatient(saved.getPatientId(), cmd.getPractitionerId());
        }
        // 发布事件：皮试记录已保存
        domainEventPublisher.publish(new AllergyIntoleranceRecordedEvent(saved.getId(), saved.getEncounterId(), saved.getPatientId(),
            saved.getMedicationId(), saved.getClinicalStatusEnum(), saved.getVerificationStatusEnum()));
        return saved.getId();
    }

    /**
     * 保存主诉过敏史（医生或住院护士）。有效过敏原会作废该患者 NKDA。
     *
     * @param cmd 保存命令
     * @return 记录标识
     * @throws BusinessException 参数缺失、更新目标不存在或试图改皮试行时抛出
     */
    public Long saveDeclaredAllergyRecord(SaveDeclaredAllergyRecordCommand cmd) {
        if (cmd == null) {
            throw new BusinessException(ErrorCodeConstants.ALLERGY_INTOLERANCE_INVALID_ARGUMENT, "保存命令不能为空");
        }
        if (StringUtils.isEmpty(cmd.getDescription())) {
            throw new BusinessException(ErrorCodeConstants.ALLERGY_INTOLERANCE_INVALID_ARGUMENT, "过敏原名称不能为空");
        }
        String allergenName = cmd.getDescription().trim();
        if (allergenName.length() > 255) {
            throw new BusinessException(ErrorCodeConstants.ALLERGY_INTOLERANCE_INVALID_ARGUMENT, "过敏原名称长度不能超过255");
        }
        AllergyIntolerance entity;
        if (cmd.getId() == null) {
            entity = new AllergyIntolerance();
        } else {
            entity = repository.findById(cmd.getId()).orElseThrow(
                () -> new BusinessException(ErrorCodeConstants.ALLERGY_INTOLERANCE_NOT_FOUND, "过敏记录不存在"));
            // 皮试行只能在皮试页维护，禁止主诉接口覆盖
            if (entity.isSkinTestRecord()) {
                throw new BusinessException(ErrorCodeConstants.ALLERGY_INTOLERANCE_RULE_CONFLICT, "皮试阳性记录请在皮试页维护");
            }
        }
        entity.setRequestId(null);
        entity.setEncounterId(cmd.getEncounterId());
        entity.setPatientId(cmd.getPatientId());
        entity.setTypeEnum(AllergyRecordType.DECLARED.getCode());
        entity.setCategoryCode(cmd.getCategoryCode());
        entity.setDescription(allergenName);
        String clinicalStatus = cmd.getClinicalStatusEnum();
        // 未传临床状态时按仍有效过敏写入
        if (StringUtils.isEmpty(clinicalStatus)) {
            clinicalStatus = ClinicalStatus.ACTIVE.getCode();
        }
        entity.setClinicalStatusEnum(clinicalStatus);
        entity.setVerificationStatusEnum(cmd.getVerificationStatusEnum());
        entity.setCriticalityEnum(cmd.getCriticalityEnum());
        entity.setSeverityEnum(cmd.getSeverityEnum());
        entity.setOnsetDateTime(cmd.getOnsetDateTime());
        LocalDateTime recordedDate = cmd.getRecordedDate();
        if (recordedDate == null) {
            recordedDate = LocalDateTime.now();
        }
        entity.setRecordedDate(recordedDate);
        entity.setPractitionerId(cmd.getPractitionerId());
        entity.setNote(cmd.getNote());
        entity.assertRequiredReferences();
        entity.assertStatusPairPresent();
        entity.assertPractitionerReference();
        AllergyIntolerance saved = repository.save(entity);
        // 有效主诉过敏与 NKDA 互斥
        if (saved.isActiveAllergen()) {
            this.inactivateNkdaForPatient(saved.getPatientId(), cmd.getPractitionerId());
        }
        domainEventPublisher.publish(new AllergyIntoleranceRecordedEvent(saved.getId(), saved.getEncounterId(), saved.getPatientId(),
            saved.getMedicationId(), saved.getClinicalStatusEnum(), saved.getVerificationStatusEnum()));
        return saved.getId();
    }

    /**
     * 登记「无已知过敏」。患者当前存在有效过敏原则拒绝。
     *
     * @param cmd 保存命令
     * @return 记录标识；已有 NKDA 则返回原记录
     * @throws BusinessException 仍有有效过敏原或参数缺失时抛出
     */
    public Long saveNkda(SaveNkdaCommand cmd) {
        if (cmd == null || cmd.getPatientId() == null || cmd.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.ALLERGY_INTOLERANCE_INVALID_ARGUMENT, "就诊、患者标识不能为空");
        }
        List<AllergyIntolerance> existing = repository.findByPatientId(cmd.getPatientId());
        for (AllergyIntolerance row : existing) {
            if (row == null || Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            if (row.isActiveAllergen()) {
                throw new BusinessException(ErrorCodeConstants.ALLERGY_INTOLERANCE_RULE_CONFLICT, "存在有效过敏史，不能登记无已知过敏");
            }
            if (row.isNkdaRecord()) {
                return row.getId();
            }
        }
        AllergyIntolerance entity = new AllergyIntolerance();
        entity.setRequestId(null);
        entity.setEncounterId(cmd.getEncounterId());
        entity.setPatientId(cmd.getPatientId());
        entity.setTypeEnum(AllergyRecordType.NKDA.getCode());
        entity.setDescription("无已知过敏");
        entity.setClinicalStatusEnum(ClinicalStatus.ACTIVE.getCode());
        String verification = cmd.getVerificationStatusEnum();
        // 护士主诉默认未确诊；医生入口会传入 CONFIRMED
        if (StringUtils.isEmpty(verification)) {
            verification = AllergyVerificationStatus.UNCONFIRMED.getCode();
        }
        entity.setVerificationStatusEnum(verification);
        LocalDateTime recordedDate = cmd.getRecordedDate();
        if (recordedDate == null) {
            recordedDate = LocalDateTime.now();
        }
        entity.setRecordedDate(recordedDate);
        entity.setPractitionerId(cmd.getPractitionerId());
        entity.assertRequiredReferences();
        entity.assertStatusPairPresent();
        entity.assertPractitionerReference();
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
        // 前置校验：BR-ALI-003 删除必须基于记录 ID
        if (cmd.getRecordId() == null) {
            throw new BusinessException(ErrorCodeConstants.ALLERGY_INTOLERANCE_INVALID_ARGUMENT, "记录标识不能为空");
        }
        AllergyIntolerance entity = repository.findById(cmd.getRecordId()).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.ALLERGY_INTOLERANCE_NOT_FOUND, "过敏记录不存在"));
        entity.assertDeletable();
        entity.markDeleted(cmd.getOperatorId());
        // 持久化
        repository.save(entity);
        // 发布事件：记录已删除
        domainEventPublisher.publish(new AllergyIntoleranceRemovedEvent(entity.getId(), entity.getEncounterId(), entity.getPatientId()));
    }

    /**
     * 删除主诉或 NKDA（皮试行禁止由此删除）。
     *
     * @param cmd 删除命令
     * @throws BusinessException 记录不存在、皮试行或入参非法时抛出
     */
    public void deleteAllergyHistoryRecord(DeleteSkinTestRecordCommand cmd) {
        if (cmd == null || cmd.getRecordId() == null) {
            throw new BusinessException(ErrorCodeConstants.ALLERGY_INTOLERANCE_INVALID_ARGUMENT, "记录标识不能为空");
        }
        AllergyIntolerance entity = repository.findById(cmd.getRecordId()).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.ALLERGY_INTOLERANCE_NOT_FOUND, "过敏记录不存在"));
        if (entity.isSkinTestRecord()) {
            throw new BusinessException(ErrorCodeConstants.ALLERGY_INTOLERANCE_RULE_CONFLICT, "皮试记录请在皮试页删除");
        }
        entity.assertDeletable();
        entity.markDeleted(cmd.getOperatorId());
        repository.save(entity);
        domainEventPublisher.publish(new AllergyIntoleranceRemovedEvent(entity.getId(), entity.getEncounterId(), entity.getPatientId()));
    }

    /**
     * 作废患者当前有效的 NKDA 声明（皮试阳性或新登记过敏原时）。
     *
     * @param patientId  患者主键
     * @param operatorId 操作人
     */
    private void inactivateNkdaForPatient(Long patientId, Long operatorId) {
        if (patientId == null) {
            return;
        }
        List<AllergyIntolerance> rows = repository.findByPatientId(patientId);
        for (AllergyIntolerance row : rows) {
            if (row == null || Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            if (!row.isNkdaRecord()) {
                continue;
            }
            row.markDeleted(operatorId);
            repository.save(row);
            domainEventPublisher.publish(new AllergyIntoleranceRemovedEvent(row.getId(), row.getEncounterId(), row.getPatientId()));
        }
    }
}
