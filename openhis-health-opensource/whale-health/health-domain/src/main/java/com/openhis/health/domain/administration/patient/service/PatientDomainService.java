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

package com.openhis.health.domain.administration.patient.service;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.health.domain.administration.patient.command.*;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.entity.PatientIdentifier;
import com.openhis.health.domain.administration.patient.entity.PatientStudent;
import com.openhis.health.domain.administration.patient.repository.PatientIdentifierRepository;
import com.openhis.health.domain.administration.patient.repository.PatientRepository;
import com.openhis.health.domain.administration.patient.repository.PatientStudentRepository;
import com.openhis.health.domain.administration.patient.valueobject.Address;
import com.openhis.health.domain.administration.patient.valueobject.BloodType;
import com.openhis.health.domain.administration.patient.valueobject.ContactPerson;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.IdentifierState;
import com.openhis.health.domain.share.events.administration.patient.*;
import com.openhis.health.domain.share.utils.AgeCalculatorUtil;
import com.openhis.health.domain.share.utils.CommandBatchUtil;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import com.openhis.whale.core.infrastructure.util.ChineseConvertUtils;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 患者写侧领域服务：建档、标识符、学生扩展档与领域事件；批量写路径遵循 B.14 先落库后发布。
 */
@Service
@RequiredArgsConstructor
public class PatientDomainService {

    private final PatientRepository patientRepository;

    private final PatientIdentifierRepository patientIdentifierRepository;

    private final PatientStudentRepository patientStudentRepository;

    private final IDomainEventPublisher domainEventPublisher;

    private final ICurrentUser currentUser;

    private final BusinessSequenceService businessSequenceService;

    /**
     * 新建患者档案。
     *
     * @param command 建档命令
     * @return 首条患者标识
     */
    public Long registerPatient(RegisterPatientCommand command) {
        LocalDateTime occurredAt = command.getOccurredAt();
        // 业务分支处理。
        if (occurredAt == null) {
            occurredAt = LocalDateTime.now();
        }
        List<RegisterPatientCommand.RegisterPatientItemCommand> items = command.resolveItems();
        this.assertBatchSize(items.size());
        this.assertNoDuplicateIdCardInBatch(items);
        // 患者主档为非按日流水：一次性批量取号，前缀取 AggregateType.PATIENT，避免先 TMP 再二次落库。
        List<String> patientBusNos = businessSequenceService.getSeq(AggregateTypeConstants.PATIENT, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH, items.size());
        List<Patient> patients = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            RegisterPatientCommand.RegisterPatientItemCommand item = items.get(index);
            Patient patient = new Patient();
            patient.setName(item.getName());
            patient.setGenderEnum(item.getGenderEnum());
            patient.setBirthDate(item.getBirthDate());
            patient.setIdCard(this.normalizeIdCard(item.getIdCard()));
            patient.setPhone(item.getPhone());
            patient.setTempFlag(item.getTempFlag());
            patient.setOrganizationId(item.getOrganizationId());
            patient.assertRegisterRequiredFields();
            this.assertIdCardUnique(item.getIdCard(), null);
            patient.initStatusByTempFlag();
            patient.setPyStr(this.buildPyStr(item.getName()));
            patient.setWbStr(this.buildWbStr(item.getName()));
            patient.setBusNo(patientBusNos.get(index));
            patients.add(patient);
        }
        List<Patient> savedPatients = patientRepository.saveAll(patients);

        List<PatientIdentifier> allIdentifiers = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            RegisterPatientCommand.RegisterPatientItemCommand item = items.get(index);
            Patient patient = savedPatients.get(index);
            this.collectIdentifierEntities(allIdentifiers, patient.getId(), item.getIdentifierItems(), true);
        }
        this.assertIdentifierUniqueAcrossPatientsBatch(allIdentifiers);
        // 业务分支处理。
        if (!allIdentifiers.isEmpty()) {
            patientIdentifierRepository.saveAll(allIdentifiers);
        }
        for (Patient patient : savedPatients) {
            domainEventPublisher.publish(new PatientRegisteredEvent(patient.getId(), patient.getBusNo(), patient.getName(),
                patient.getGenderEnum(), patient.getStatusEnum(), patient.getTempFlag(), patient.getOrganizationId()));
        }
        return savedPatients.get(0).getId();
    }

    /**
     * 修改患者档案。
     *
     * @param command 修改命令
     */
    public void modifyPatientProfile(ModifyPatientProfileCommand command) {
        List<ModifyPatientProfileCommand.ModifyPatientProfileItemCommand> items = command.resolveItems();
        this.assertBatchSize(items.size());
        Map<Long, Patient> patientMap = loadPatientMap(CommandBatchUtil.extractIds(items, ModifyPatientProfileCommand.ModifyPatientProfileItemCommand::getPatientId));
        List<Patient> toSave = new ArrayList<>();
        Map<Long, List<PatientIdentifierItemCommand>> identifierInput = new HashMap<>();
        Map<Long, List<String>> changedFieldMap = new HashMap<>();
        for (ModifyPatientProfileCommand.ModifyPatientProfileItemCommand item : items) {
            Patient patient = this.requirePatient(patientMap, item.getPatientId());
            List<String> changedFields = this.collectProfileChangedFields(patient, item);
            patient.assertProfileModifiable();
            this.assertIdCardUnique(item.getIdCard(), patient.getId());
            // DOC-CONFLICT: BR-PAT-016 要求年龄与生日一致性校验，但档案修改写侧未接收 age 字段。
            // 当前实现以 birthDate 为写侧唯一权威输入，年龄由读侧派生计算。
            patient.setName(item.getName());
            patient.setNameJson(item.getNameJson());
            patient.setGenderEnum(item.getGenderEnum());
            patient.setBirthDate(item.getBirthDate());
            patient.setIdCard(this.normalizeIdCard(item.getIdCard()));
            patient.setMaritalStatusEnum(item.getMaritalStatusEnum());
            patient.setPrfsEnum(item.getPrfsEnum());
            patient.setPhone(item.getPhone());
            patient.setAddress(this.buildAddress(item));
            patient.setAddressExtendJson(item.getAddressExtendJson());
            patient.setNationalityCode(item.getNationalityCode());
            patient.setWorkCompany(item.getWorkCompany());
            patient.setNativePlace(item.getNativePlace());
            patient.setCountryCode(item.getCountryCode());
            patient.setLinkName(this.buildContactPerson(item));
            patient.setLinkJsons(item.getLinkJsons());
            patient.setBloodType(this.buildBloodType(item));
            patient.setPyStr(this.buildPyStr(item.getName()));
            patient.setWbStr(this.buildWbStr(item.getName()));
            toSave.add(patient);
            identifierInput.put(patient.getId(), item.getIdentifierItems());
            changedFieldMap.put(patient.getId(), changedFields);
        }
        patientRepository.saveAll(toSave);
        this.syncIdentifiersBatch(identifierInput);
        Long operatorId = requireOperatorId();
        for (Patient patient : toSave) {
            List<String> changedFields = changedFieldMap.getOrDefault(patient.getId(), Collections.singletonList("profile"));
            domainEventPublisher.publish(new PatientProfileModifiedEvent(patient.getId(), changedFields, operatorId));
        }
    }

    /**
     * 同步患者标识子表。
     *
     * @param command 同步命令
     */
    public void syncPatientIdentifiers(SyncPatientIdentifiersCommand command) {
        List<SyncPatientIdentifiersCommand.SyncPatientIdentifiersItemCommand> items = command.resolveItems();
        this.assertBatchSize(items.size());
        Map<Long, Patient> patientMap = loadPatientMap(CommandBatchUtil.extractIds(items, SyncPatientIdentifiersCommand.SyncPatientIdentifiersItemCommand::getPatientId));
        Map<Long, List<PatientIdentifierItemCommand>> input = new HashMap<>();
        for (SyncPatientIdentifiersCommand.SyncPatientIdentifiersItemCommand item : items) {
            Patient patient = this.requirePatient(patientMap, item.getPatientId());
            patient.assertIdentifierSyncAllowed();
            input.put(item.getPatientId(), item.getIdentifierItems());
        }
        this.syncIdentifiersBatch(input);
        for (SyncPatientIdentifiersCommand.SyncPatientIdentifiersItemCommand item : items) {
            int count = 0;
            // 业务分支处理。
            if (item.getIdentifierItems() != null) {
                count = item.getIdentifierItems().size();
            }
            domainEventPublisher.publish(new PatientIdentifiersSyncedEvent(item.getPatientId(), count));
        }
    }

    /**
     * 明确患者身份。
     *
     * @param command 命令
     */
    public void confirmPatientIdentity(ConfirmPatientIdentityCommand command) {
        List<ConfirmPatientIdentityCommand.ConfirmPatientIdentityItemCommand> items = command.resolveItems();
        this.assertBatchSize(items.size());
        Map<Long, Patient> patientMap = loadPatientMap(CommandBatchUtil.extractIds(items, ConfirmPatientIdentityCommand.ConfirmPatientIdentityItemCommand::getPatientId));
        List<Patient> toSave = new ArrayList<>();
        for (ConfirmPatientIdentityCommand.ConfirmPatientIdentityItemCommand item : items) {
            Patient patient = this.requirePatient(patientMap, item.getPatientId());
            patient.confirmIdentity();
            toSave.add(patient);
        }
        patientRepository.saveAll(toSave);
        for (Patient patient : toSave) {
            domainEventPublisher.publish(new PatientIdentityConfirmedEvent(patient.getId(), patient.getBusNo(),
                patient.getStatusEnum(), LocalDateTime.now()));
        }
    }

    /**
     * 更新患者手机号。
     *
     * @param command 命令
     */
    public void updatePatientPhone(UpdatePatientPhoneCommand command) {
        List<UpdatePatientPhoneCommand.UpdatePatientPhoneItemCommand> items = command.resolveItems();
        this.assertBatchSize(items.size());
        Map<Long, Patient> patientMap = loadPatientMap(CommandBatchUtil.extractIds(items, UpdatePatientPhoneCommand.UpdatePatientPhoneItemCommand::getPatientId));
        List<Patient> toSave = new ArrayList<>();
        for (UpdatePatientPhoneCommand.UpdatePatientPhoneItemCommand item : items) {
            Patient patient = this.requirePatient(patientMap, item.getPatientId());
            patient.assertPhoneUpdatable();
            patient.assertPhoneFormatValid(item.getPhone());
            patient.setPhone(item.getPhone());
            toSave.add(patient);
        }
        patientRepository.saveAll(toSave);
        for (Patient patient : toSave) {
            domainEventPublisher.publish(new PatientPhoneModifiedEvent(patient.getId(), patient.getPhone()));
        }
    }

    /**
     * 停用患者。
     *
     * @param command 命令
     * @preconditions 应用服务层需在调用前完成跨聚合引用校验（如进行中就诊、活跃账务等）。
     */
    public void deactivatePatient(DeactivatePatientCommand command) {
        List<DeactivatePatientCommand.DeactivatePatientItemCommand> items = command.resolveItems();
        this.assertBatchSize(items.size());
        Map<Long, Patient> patientMap = loadPatientMap(CommandBatchUtil.extractIds(items, DeactivatePatientCommand.DeactivatePatientItemCommand::getPatientId));
        List<Patient> toSave = new ArrayList<>();
        for (DeactivatePatientCommand.DeactivatePatientItemCommand item : items) {
            Patient patient = this.requirePatient(patientMap, item.getPatientId());
            patient.deactivate();
            toSave.add(patient);
        }
        patientRepository.saveAll(toSave);
        for (int index = 0; index < toSave.size(); index++) {
            Patient patient = toSave.get(index);
            DeactivatePatientCommand.DeactivatePatientItemCommand item = items.get(index);
            domainEventPublisher.publish(new PatientDeactivatedEvent(patient.getId(), patient.getStatusEnum(), item.getReason()));
        }
    }

    /**
     * 重新启用患者。
     *
     * @param command 命令
     */
    public void reactivatePatient(ReactivatePatientCommand command) {
        List<ReactivatePatientCommand.ReactivatePatientItemCommand> items = command.resolveItems();
        this.assertBatchSize(items.size());
        Map<Long, Patient> patientMap = loadPatientMap(CommandBatchUtil.extractIds(items, ReactivatePatientCommand.ReactivatePatientItemCommand::getPatientId));
        List<Patient> toSave = new ArrayList<>();
        for (ReactivatePatientCommand.ReactivatePatientItemCommand item : items) {
            Patient patient = this.requirePatient(patientMap, item.getPatientId());
            patient.reactivate();
            toSave.add(patient);
        }
        patientRepository.saveAll(toSave);
        for (Patient patient : toSave) {
            domainEventPublisher.publish(new PatientReactivatedEvent(patient.getId(), patient.getStatusEnum()));
        }
    }

    /**
     * 合并患者。
     *
     * @param command 命令
     */
    public void mergePatient(MergePatientCommand command) {
        List<MergePatientCommand.MergePatientItemCommand> items = command.resolveItems();
        this.assertBatchSize(items.size());
        Set<Long> allIds = new HashSet<>();
        for (MergePatientCommand.MergePatientItemCommand item : items) {
            allIds.add(item.getSourcePatientId());
            allIds.add(item.getTargetPatientId());
        }
        Map<Long, Patient> patientMap = loadPatientMap(new ArrayList<>(allIds));
        List<Patient> toSave = new ArrayList<>();
        for (MergePatientCommand.MergePatientItemCommand item : items) {
            Patient source = this.requirePatient(patientMap, item.getSourcePatientId());
            Patient target = this.requirePatient(patientMap, item.getTargetPatientId());
            source.assertMergeTargetValid(target.getId(), target.getStatusEnum());
            source.markMergedTo(target.getId(), LocalDateTime.now());
            toSave.add(source);
        }
        patientRepository.saveAll(toSave);
        Long operatorId = requireOperatorId();
        for (int index = 0; index < toSave.size(); index++) {
            Patient source = toSave.get(index);
            MergePatientCommand.MergePatientItemCommand item = items.get(index);
            domainEventPublisher.publish(new PatientMergedEvent(source.getId(), item.getTargetPatientId(), source.getStatusEnum(),
                source.getMergedAt(), item.getReason(), operatorId));
        }
    }

    /**
     * 标记患者死亡。
     *
     * @param command 命令
     */
    public void markPatientDeceased(MarkPatientDeceasedCommand command) {
        List<MarkPatientDeceasedCommand.MarkPatientDeceasedItemCommand> items = command.resolveItems();
        this.assertBatchSize(items.size());
        Map<Long, Patient> patientMap = loadPatientMap(CommandBatchUtil.extractIds(items, MarkPatientDeceasedCommand.MarkPatientDeceasedItemCommand::getPatientId));
        List<Patient> toSave = new ArrayList<>();
        for (MarkPatientDeceasedCommand.MarkPatientDeceasedItemCommand item : items) {
            Patient patient = this.requirePatient(patientMap, item.getPatientId());
            patient.assertCanMarkDeceased();
            patient.markDeceased(item.getDeceasedDate(), item.getDeceasedReason());
            toSave.add(patient);
        }
        patientRepository.saveAll(toSave);
        for (Patient patient : toSave) {
            domainEventPublisher.publish(new PatientDeceasedMarkedEvent(patient.getId(), patient.getDeceasedDate(),
                patient.getDeceasedReason()));
        }
    }

    /**
     * 废弃临时建档。
     *
     * @param command 命令
     * @preconditions 应用服务层需在调用前完成跨聚合引用校验，确保无进行中业务引用。
     */
    public void discardDraftPatient(DiscardDraftPatientCommand command) {
        List<DiscardDraftPatientCommand.DiscardDraftPatientItemCommand> items = command.resolveItems();
        this.assertBatchSize(items.size());
        Map<Long, Patient> patientMap = loadPatientMap(CommandBatchUtil.extractIds(items, DiscardDraftPatientCommand.DiscardDraftPatientItemCommand::getPatientId));
        List<Patient> toSave = new ArrayList<>();
        for (DiscardDraftPatientCommand.DiscardDraftPatientItemCommand item : items) {
            Patient patient = this.requirePatient(patientMap, item.getPatientId());
            patient.discardDraft();
            toSave.add(patient);
        }
        patientRepository.saveAll(toSave);
        for (Patient patient : toSave) {
            domainEventPublisher.publish(new PatientDraftDiscardedEvent(patient.getId(), patient.getBusNo()));
        }
    }

    /**
     * 逻辑删除患者。
     *
     * @param command 命令
     * @preconditions 应用服务层需在调用前完成跨聚合活跃引用校验，领域层仅处理聚合内删除语义。
     */
    public void deletePatient(DeletePatientCommand command) {
        List<DeletePatientCommand.DeletePatientItemCommand> items = command.resolveItems();
        this.assertBatchSize(items.size());
        Map<Long, Patient> patientMap = loadPatientMap(CommandBatchUtil.extractIds(items, DeletePatientCommand.DeletePatientItemCommand::getPatientId));
        List<Patient> toSave = new ArrayList<>();
        Long operatorId = requireOperatorId();
        for (DeletePatientCommand.DeletePatientItemCommand item : items) {
            Patient patient = this.requirePatient(patientMap, item.getPatientId());
            patient.assertCanBeDeleted();
            patient.markDeleted(operatorId);
            toSave.add(patient);
        }
        patientRepository.saveAll(toSave);
        for (Patient patient : toSave) {
            domainEventPublisher.publish(new PatientDeletedEvent(patient.getId(), patient.getBusNo(), patient.getDeletedAt()));
        }
    }

    /**
     * 新增或更新学生资质。
     *
     * @param command 命令
     */
    public void upsertPatientStudentRecord(UpsertPatientStudentRecordCommand command) {
        List<UpsertPatientStudentRecordCommand.UpsertPatientStudentRecordItemCommand> items = command.resolveItems();
        this.assertBatchSize(items.size());
        Map<Long, Patient> patientMap = loadPatientMap(CommandBatchUtil.extractIds(items, UpsertPatientStudentRecordCommand.UpsertPatientStudentRecordItemCommand::getPatientId));
        Set<Long> studentIds = new HashSet<>();
        for (UpsertPatientStudentRecordCommand.UpsertPatientStudentRecordItemCommand item : items) {
            // 业务分支处理。
            if (item.getStudentId() != null) {
                studentIds.add(item.getStudentId());
            }
        }
        Map<Long, PatientStudent> studentMap = loadStudentMap(studentIds);
        List<PatientStudent> toSave = new ArrayList<>();
        for (UpsertPatientStudentRecordCommand.UpsertPatientStudentRecordItemCommand item : items) {
            Patient patient = this.requirePatient(patientMap, item.getPatientId());
            patient.assertStudentRecordUpsertable(this.normalizeIdCard(item.getIdCard()));
            PatientStudent student = this.resolveStudentEntity(patient, item, studentMap);
            student.assertValid();
            student.setPatientId(patient.getId());
            toSave.add(student);
        }
        List<PatientStudent> savedStudents = patientStudentRepository.saveAll(toSave);
        for (PatientStudent student : savedStudents) {
            domainEventPublisher.publish(new PatientStudentRecordUpsertedEvent(student.getPatientId(), student.getId(),
                student.getStudentNo(), student.getSchoolStatus()));
        }
    }

    /**
     * 停用公费学生资质。
     *
     * @param command 命令
     */
    public void disablePatientStudentRecord(DisablePatientStudentRecordCommand command) {
        List<DisablePatientStudentRecordCommand.DisablePatientStudentRecordItemCommand> items = command.resolveItems();
        this.assertBatchSize(items.size());
        Map<Long, Patient> patientMap = loadPatientMap(CommandBatchUtil.extractIds(items, DisablePatientStudentRecordCommand.DisablePatientStudentRecordItemCommand::getPatientId));
        Set<Long> studentIds = new HashSet<>();
        for (DisablePatientStudentRecordCommand.DisablePatientStudentRecordItemCommand item : items) {
            studentIds.add(item.getStudentId());
        }
        Map<Long, PatientStudent> studentMap = loadStudentMap(studentIds);
        List<PatientStudent> toSave = new ArrayList<>();
        Long operatorId = requireOperatorId();
        for (DisablePatientStudentRecordCommand.DisablePatientStudentRecordItemCommand item : items) {
            Patient patient = this.requirePatient(patientMap, item.getPatientId());
            patient.assertStudentRecordDisableAllowed();
            PatientStudent student = studentMap.get(item.getStudentId());
            // 业务分支处理。
            if (student == null || !student.getPatientId().equals(item.getPatientId())) {
                throw new BusinessException(ErrorCodeConstants.PATIENT_NOT_FOUND, "学生资质记录不存在");
            }
            student.disable(operatorId);
            toSave.add(student);
        }
        patientStudentRepository.saveAll(toSave);
        for (PatientStudent student : toSave) {
            domainEventPublisher.publish(new PatientStudentRecordDisabledEvent(student.getPatientId(), student.getStudentNo()));
        }
    }

    /**
     * 校验身份证是否唯一。
     *
     * @param idCard 身份证号
     * @param patientId 患者标识
     */
    private void assertIdCardUnique(String idCard, Long patientId) {
        // 业务分支处理。
        if (StringUtils.isBlank(idCard)) {
            return;
        }
        String normalizedIdCard = this.normalizeIdCard(idCard);
        // 业务分支处理。
        if (!normalizedIdCard.matches(GeneralSharedConstants.ID_CARD_REGEX)) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_ARGUMENT, "身份证号格式不正确");
        }
        // 业务分支处理。
        if (patientId == null) {
            // DOC-CONFLICT: BR-PAT-002 要求身份证号全局唯一（含已逻辑删除记录），
            // 因此唯一性校验不追加  过滤条件。
            if (patientRepository.existsByIdCard(normalizedIdCard)) {
                throw new BusinessException(ErrorCodeConstants.PATIENT_ID_CARD_CONFLICT, "身份证号已存在");
            }
        } else {
            // DOC-CONFLICT: BR-PAT-002 要求身份证号全局唯一（含已逻辑删除记录），
            // 修改场景同样按全局唯一口径校验。
            if (patientRepository.existsByIdCardAndIdNot(normalizedIdCard, patientId)) {
                throw new BusinessException(ErrorCodeConstants.PATIENT_ID_CARD_CONFLICT, "身份证号已存在");
            }
        }
    }

    /**
     * 批量同步患者证件集合。
     *
     * @param identifierInput 按患者分组的证件输入
     */
    private void syncIdentifiersBatch(Map<Long, List<PatientIdentifierItemCommand>> identifierInput) {
        Set<Long> patientIds = identifierInput.keySet();
        List<PatientIdentifier> existingAll = patientIdentifierRepository.findAllByPatientIdIn(patientIds);
        Map<Long, List<PatientIdentifier>> existingMap = new HashMap<>();
        for (PatientIdentifier identifier : existingAll) {
            List<PatientIdentifier> bucket = existingMap.computeIfAbsent(identifier.getPatientId(), key -> new ArrayList<>());
            bucket.add(identifier);
        }
        List<PatientIdentifier> toSave = new ArrayList<>();
        for (Map.Entry<Long, List<PatientIdentifierItemCommand>> entry : identifierInput.entrySet()) {
            Long patientId = entry.getKey();
            List<PatientIdentifierItemCommand> inputList = entry.getValue();
            Map<String, PatientIdentifier> existedByKey = new HashMap<>();
            List<PatientIdentifier> existedList = existingMap.get(patientId);
            // 业务分支处理。
            if (existedList != null) {
                for (PatientIdentifier existed : existedList) {
                    existedByKey.put(this.buildIdentifierKey(existed.getTypeCode(), existed.getIdentifierNo()), existed);
                }
            }
            Set<String> incomingKeys = new HashSet<>();
            // 业务分支处理。
            if (inputList != null) {
                for (PatientIdentifierItemCommand item : inputList) {
                    String key = this.buildIdentifierKey(item.getTypeCode(), item.getIdentifierNo());
                    incomingKeys.add(key);
                    PatientIdentifier entity = existedByKey.get(key);
                    // 业务分支处理。
                    if (entity == null) {
                        entity = this.buildIdentifierEntity(patientId, item, true);
                    } else {
                        entity.setTypeCode(item.getTypeCode());
                        entity.setIdentifierNo(item.getIdentifierNo());
                        entity.setStateEnum(item.getStateEnum());
                        entity.setStartTime(item.getStartTime());
                        entity.setEndTime(item.getEndTime());
                    }
                    entity.assertValid();
                    this.assertIdentifierStatePriority(patientId, entity, existingMap);
                    entity.markOldWhenExpired(LocalDateTime.now());
                    toSave.add(entity);
                }
            }
            // 业务分支处理。
            if (existedList != null) {
                for (PatientIdentifier existed : existedList) {
                    String key = this.buildIdentifierKey(existed.getTypeCode(), existed.getIdentifierNo());
                    // 业务分支处理。
                    if (!incomingKeys.contains(key)) {
                        existed.setIsDeleted(Boolean.TRUE);
                        existed.setDeletedAt(LocalDateTime.now());
                        existed.transitState(IdentifierState.OLD);
                        toSave.add(existed);
                    }
                }
            }
        }
        this.assertIdentifierUniqueAcrossPatientsBatch(toSave);
        // 业务分支处理。
        if (!toSave.isEmpty()) {
            patientIdentifierRepository.saveAll(toSave);
        }
    }

    /**
     * 批量校验标识跨患者唯一性。
     *
     * @param identifiers 待校验标识集合
     */
    private void assertIdentifierUniqueAcrossPatientsBatch(List<PatientIdentifier> identifiers) {
        // 业务分支处理。
        if (identifiers == null || identifiers.isEmpty()) {
            return;
        }
        Set<String> typeCodes = new HashSet<>();
        Set<String> identifierNos = new HashSet<>();
        Map<String, Long> incomingOwners = new HashMap<>();
        for (PatientIdentifier identifier : identifiers) {
            // 业务分支处理。
            if (identifier == null || Boolean.TRUE.equals(identifier.getIsDeleted())) {
                continue;
            }
            // 业务分支处理。
            if (StringUtils.isBlank(identifier.getTypeCode()) || StringUtils.isBlank(identifier.getIdentifierNo())) {
                continue;
            }
            typeCodes.add(identifier.getTypeCode());
            identifierNos.add(identifier.getIdentifierNo());
            String key = this.buildIdentifierKey(identifier.getTypeCode(), identifier.getIdentifierNo());
            Long existingOwner = incomingOwners.get(key);
            // 业务分支处理。
            if (existingOwner != null && !Objects.equals(existingOwner, identifier.getPatientId())) {
                throw new BusinessException(ErrorCodeConstants.PATIENT_IDENTIFIER_CONFLICT, "证件标识在其他患者下已存在");
            }
            incomingOwners.put(key, identifier.getPatientId());
        }
        // 业务分支处理。
        if (typeCodes.isEmpty() || identifierNos.isEmpty()) {
            return;
        }
        List<PatientIdentifier> existed = patientIdentifierRepository.findAllByTypeCodeInAndIdentifierNoIn(
            typeCodes, identifierNos);
        Map<String, Long> existedOwners = new HashMap<>();
        for (PatientIdentifier old : existed) {
            // 业务分支处理。
            if (old == null || StringUtils.isBlank(old.getTypeCode()) || StringUtils.isBlank(old.getIdentifierNo())) {
                continue;
            }
            existedOwners.put(this.buildIdentifierKey(old.getTypeCode(), old.getIdentifierNo()), old.getPatientId());
        }
        for (PatientIdentifier identifier : identifiers) {
            // 业务分支处理。
            if (identifier == null || Boolean.TRUE.equals(identifier.getIsDeleted())) {
                continue;
            }
            // 业务分支处理。
            if (StringUtils.isBlank(identifier.getTypeCode()) || StringUtils.isBlank(identifier.getIdentifierNo())) {
                continue;
            }
            String key = this.buildIdentifierKey(identifier.getTypeCode(), identifier.getIdentifierNo());
            Long existedOwner = existedOwners.get(key);
            // 业务分支处理。
            if (existedOwner != null && !Objects.equals(existedOwner, identifier.getPatientId())) {
                throw new BusinessException(ErrorCodeConstants.PATIENT_IDENTIFIER_CONFLICT, "证件标识在其他患者下已存在");
            }
        }
    }

    /**
     * 校验同一患者同类型证件状态优先级规则。
     *
     * @param patientId 患者标识
     * @param entity 当前证件
     * @param existingMap 现存证件集合
     */
    private void assertIdentifierStatePriority(Long patientId, PatientIdentifier entity,
                                               Map<Long, List<PatientIdentifier>> existingMap) {
        List<PatientIdentifier> existed = existingMap.get(patientId);
        // 业务分支处理。
        if (existed == null) {
            return;
        }
        for (PatientIdentifier old : existed) {
            // 业务分支处理。
            if (!old.getTypeCode().equals(entity.getTypeCode())) {
                continue;
            }
            // 业务分支处理。
            if (Boolean.TRUE.equals(old.getIsDeleted())) {
                continue;
            }
            // 业务分支处理。
            if (entity.getStateEnum() == null || old.getStateEnum() == null) {
                continue;
            }
            // 同一证件类型内，常用或官方状态只允许一条（BR-PAT-004），委托子实体 isUsualOrOfficial() 判断。
            if (entity.isUsualOrOfficial() && old.isUsualOrOfficial() && !Objects.equals(entity.getId(), old.getId())) {
                throw new BusinessException(ErrorCodeConstants.PATIENT_IDENTIFIER_CONFLICT, "同一证件类型仅允许一条常用或官方状态记录");
            }
        }
    }

    /**
     * 构建证件拼接主键。
     *
     * @param typeCode 证件类型
     * @param identifierNo 证件号
     * @return 业务键
     */
    private String buildIdentifierKey(String typeCode, String identifierNo) {
        return typeCode + "\u0001" + identifierNo;
    }

    /**
     * 构建证件实体。
     *
     * @param patientId 患者标识
     * @param item 输入数据
     * @param normalizeState 是否归一化状态
     * @return 证件实体
     */
    private PatientIdentifier buildIdentifierEntity(Long patientId, PatientIdentifierItemCommand item,
                                                    boolean normalizeState) {
        PatientIdentifier identifier = new PatientIdentifier();
        identifier.setPatientId(patientId);
        identifier.setTypeCode(item.getTypeCode());
        identifier.setIdentifierNo(item.getIdentifierNo());
        identifier.setStateEnum(item.getStateEnum());
        identifier.setStartTime(item.getStartTime());
        identifier.setEndTime(item.getEndTime());
        // 业务分支处理。
        if (normalizeState) {
            identifier.normalizeState();
        }
        identifier.assertValid();
        return identifier;
    }

    /**
     * 汇总新增场景证件实体。
     *
     * @param collector 汇总集合
     * @param patientId 患者标识
     * @param identifierItems 证件输入
     * @param normalizeState 是否归一化状态
     */
    private void collectIdentifierEntities(List<PatientIdentifier> collector, Long patientId,
                                           List<PatientIdentifierItemCommand> identifierItems,
                                           boolean normalizeState) {
        // 业务分支处理。
        if (identifierItems == null || identifierItems.isEmpty()) {
            return;
        }
        for (PatientIdentifierItemCommand identifierItem : identifierItems) {
            PatientIdentifier identifier = this.buildIdentifierEntity(patientId, identifierItem, normalizeState);
            collector.add(identifier);
        }
    }

    /**
     * 构建地址值对象。
     *
     * @param item 档案修改输入
     * @return 地址对象
     */
    private Address buildAddress(ModifyPatientProfileCommand.ModifyPatientProfileItemCommand item) {
        Address address = new Address();
        address.setText(item.getAddress());
        address.setProvince(item.getAddressProvince());
        address.setCity(item.getAddressCity());
        address.setDistrict(item.getAddressDistrict());
        address.setStreet(item.getAddressStreet());
        return address;
    }

    /**
     * 构建联系人值对象。
     *
     * @param item 档案修改输入
     * @return 联系人对象
     */
    private ContactPerson buildContactPerson(ModifyPatientProfileCommand.ModifyPatientProfileItemCommand item) {
        ContactPerson person = new ContactPerson();
        person.setName(item.getLinkName());
        person.setRelationCode(item.getLinkRelationCode());
        person.setTelcom(item.getLinkTelcom());
        return person;
    }

    /**
     * 构建血型值对象。
     *
     * @param item 档案修改输入
     * @return 血型对象
     */
    private BloodType buildBloodType(ModifyPatientProfileCommand.ModifyPatientProfileItemCommand item) {
        BloodType bloodType = new BloodType();
        bloodType.setAbo(item.getBloodTypeAbo());
        bloodType.setRh(item.getBloodTypeRh());
        return bloodType;
    }

    /**
     * 加载患者映射。
     *
     * @param patientIds 患者标识集合
     * @return 患者映射
     */
    private Map<Long, Patient> loadPatientMap(List<Long> patientIds) {
        List<Patient> patients = patientRepository.findAllByIdIn(patientIds);
        Map<Long, Patient> map = new HashMap<>();
        for (Patient patient : patients) {
            map.put(patient.getId(), patient);
        }
        return map;
    }

    /**
     * 加载学生记录映射。
     *
     * @param studentIds 学生记录标识集合
     * @return 学生映射
     */
    private Map<Long, PatientStudent> loadStudentMap(Set<Long> studentIds) {
        Map<Long, PatientStudent> map = new HashMap<>();
        // 业务分支处理。
        if (studentIds.isEmpty()) {
            return map;
        }
        List<PatientStudent> students = patientStudentRepository.findAllByIdIn(studentIds);
        for (PatientStudent student : students) {
            map.put(student.getId(), student);
        }
        return map;
    }

    /**
     * 获取并校验患者实体。
     *
     * @param map 患者映射
     * @param patientId 患者标识
     * @return 患者实体
     */
    private Patient requirePatient(Map<Long, Patient> map, Long patientId) {
        Patient patient = map.get(patientId);
        // 业务分支处理。
        if (patient == null) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_NOT_FOUND, "患者不存在或已删除");
        }
        return patient;
    }

    /**
     * 校验批量输入上限。
     *
     * @param size 当前条数
     */
    private void assertBatchSize(int size) {
        // 业务分支处理。
        if (size > GeneralSharedConstants.BATCH_LIMIT) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_ARGUMENT, "批量处理超过上限");
        }
    }

    /**
     * 构建拼音检索码。
     *
     * @param name 姓名
     * @return 拼音检索码
     */
    private String buildPyStr(String name) {
        // 业务分支处理。
        if (StringUtils.isBlank(name)) {
            return null;
        }
        return ChineseConvertUtils.toPinyinFirstLetter(name).toUpperCase(Locale.ROOT);
    }

    /**
     * 构建五笔检索码。
     *
     * @param name 姓名
     * @return 五笔检索码
     */
    private String buildWbStr(String name) {
        // 业务分支处理。
        if (StringUtils.isBlank(name)) {
            return null;
        }
        return ChineseConvertUtils.toWBFirstLetter(name).toUpperCase(Locale.ROOT);
    }

    /**
     * 校验批量建档身份证号是否重复。
     *
     * @param items 建档子命令集合
     */
    private void assertNoDuplicateIdCardInBatch(List<RegisterPatientCommand.RegisterPatientItemCommand> items) {
        Set<String> idCards = new HashSet<>();
        for (RegisterPatientCommand.RegisterPatientItemCommand item : items) {
            // 业务分支处理。
            if (item == null || StringUtils.isBlank(item.getIdCard())) {
                continue;
            }
            String normalized = this.normalizeIdCard(item.getIdCard());
            // 业务分支处理。
            if (!idCards.add(normalized)) {
                throw new BusinessException(ErrorCodeConstants.PATIENT_ID_CARD_CONFLICT, "批量入参存在重复身份证号");
            }
        }
    }

    /**
     * 归一化身份证号，确保批量去重与单条唯一性校验口径一致。
     *
     * @param idCard 原始身份证号
     * @return 归一化后的身份证号
     */
    private String normalizeIdCard(String idCard) {
        // 业务分支处理。
        if (idCard == null) {
            return null;
        }
        return idCard.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 计算档案修改事件的字段变化摘要。
     *
     * @param patient 变更前患者实体
     * @param item 输入子命令
     * @return 变化字段列表
     */
    private List<String> collectProfileChangedFields(Patient patient,
                                                     ModifyPatientProfileCommand.ModifyPatientProfileItemCommand item) {
        List<String> changed = new ArrayList<>();
        this.addChangedField(changed, "name", patient.getName(), item.getName());
        this.addChangedField(changed, "nameJson", patient.getNameJson(), item.getNameJson());
        this.addChangedField(changed, "genderEnum", patient.getGenderEnum(), item.getGenderEnum());
        this.addChangedField(changed, "birthDate", patient.getBirthDate(), item.getBirthDate());
        this.addChangedField(changed, "idCard", patient.getIdCard(), item.getIdCard());
        this.addChangedField(changed, "maritalStatusEnum", patient.getMaritalStatusEnum(), item.getMaritalStatusEnum());
        this.addChangedField(changed, "prfsEnum", patient.getPrfsEnum(), item.getPrfsEnum());
        this.addChangedField(changed, "phone", patient.getPhone(), item.getPhone());
        // 提取地址各分量，Address 为 null 时各字段均为 null。
        String oldAddressText;
        String oldAddressProvince;
        String oldAddressCity;
        String oldAddressDistrict;
        String oldAddressStreet;
        if (patient.getAddress() == null) {
            oldAddressText = null;
            oldAddressProvince = null;
            oldAddressCity = null;
            oldAddressDistrict = null;
            oldAddressStreet = null;
        } else {
            oldAddressText = patient.getAddress().getText();
            oldAddressProvince = patient.getAddress().getProvince();
            oldAddressCity = patient.getAddress().getCity();
            oldAddressDistrict = patient.getAddress().getDistrict();
            oldAddressStreet = patient.getAddress().getStreet();
        }
        this.addChangedField(changed, "address", oldAddressText, item.getAddress());
        this.addChangedField(changed, "addressProvince", oldAddressProvince, item.getAddressProvince());
        this.addChangedField(changed, "addressCity", oldAddressCity, item.getAddressCity());
        this.addChangedField(changed, "addressDistrict", oldAddressDistrict, item.getAddressDistrict());
        this.addChangedField(changed, "addressStreet", oldAddressStreet, item.getAddressStreet());
        this.addChangedField(changed, "addressExtendJson", patient.getAddressExtendJson(), item.getAddressExtendJson());
        this.addChangedField(changed, "nationalityCode", patient.getNationalityCode(), item.getNationalityCode());
        this.addChangedField(changed, "workCompany", patient.getWorkCompany(), item.getWorkCompany());
        this.addChangedField(changed, "nativePlace", patient.getNativePlace(), item.getNativePlace());
        this.addChangedField(changed, "countryCode", patient.getCountryCode(), item.getCountryCode());
        // 提取紧急联系人各字段，LinkName 为 null 时各字段均为 null。
        String oldLinkName;
        Object oldLinkRelationCode;
        String oldLinkTelcom;
        if (patient.getLinkName() == null) {
            oldLinkName = null;
            oldLinkRelationCode = null;
            oldLinkTelcom = null;
        } else {
            oldLinkName = patient.getLinkName().getName();
            oldLinkRelationCode = patient.getLinkName().getRelationCode();
            oldLinkTelcom = patient.getLinkName().getTelcom();
        }
        this.addChangedField(changed, "linkName", oldLinkName, item.getLinkName());
        this.addChangedField(changed, "linkRelationCode", oldLinkRelationCode, item.getLinkRelationCode());
        this.addChangedField(changed, "linkTelcom", oldLinkTelcom, item.getLinkTelcom());
        this.addChangedField(changed, "linkJsons", patient.getLinkJsons(), item.getLinkJsons());
        // 提取血型各分量，BloodType 为 null 时各字段均为 null。
        Object oldBloodTypeAbo;
        Object oldBloodTypeRh;
        if (patient.getBloodType() == null) {
            oldBloodTypeAbo = null;
            oldBloodTypeRh = null;
        } else {
            oldBloodTypeAbo = patient.getBloodType().getAbo();
            oldBloodTypeRh = patient.getBloodType().getRh();
        }
        this.addChangedField(changed, "bloodTypeAbo", oldBloodTypeAbo, item.getBloodTypeAbo());
        this.addChangedField(changed, "bloodTypeRh", oldBloodTypeRh, item.getBloodTypeRh());
        // 业务分支处理。
        if (!Objects.equals(patient.getName(), item.getName())) {
            changed.add("pyStr");
            changed.add("wbStr");
        }
        // 业务分支处理。
        if (item.getIdentifierItems() != null) {
            changed.add("identifiers");
        }
        // 业务分支处理。
        if (changed.isEmpty()) {
            changed.add("profile");
        }
        return changed;
    }

    /**
     * 当字段变化时记录字段名。
     *
     * @param changed 变更字段集合
     * @param field 字段名
     * @param oldValue 旧值
     * @param newValue 新值
     */
    private void addChangedField(List<String> changed, String field, Object oldValue, Object newValue) {
        // 业务分支处理。
        if (!Objects.equals(oldValue, newValue)) {
            changed.add(field);
        }
    }

    /**
     * 按输入解析学生实体。
     *
     * @param patient 患者主档
     * @param item 子命令
     * @param studentMap 学生记录映射
     * @return 学生实体
     */
    private PatientStudent resolveStudentEntity(Patient patient,
                                                UpsertPatientStudentRecordCommand.UpsertPatientStudentRecordItemCommand item,
                                                Map<Long, PatientStudent> studentMap) {
        PatientStudent student = null;
        // 业务分支处理。
        if (item.getStudentId() != null) {
            student = studentMap.get(item.getStudentId());
            // 业务分支处理。
            if (student == null) {
                throw new BusinessException(ErrorCodeConstants.PATIENT_NOT_FOUND, "学籍记录不存在");
            }
        }
        // 业务分支处理。
        if (student == null) {
            student = new PatientStudent();
        }
        student.setName(item.getName());
        student.setPyStr(this.buildPyStr(item.getName()));
        student.setWbStr(this.buildWbStr(item.getName()));
        student.setGenderEnum(patient.getGenderEnum());
        student.setAge(AgeCalculatorUtil.calculateAge(patient.getBirthDate()));
        student.setStudentNo(item.getStudentNo());
        student.setIdCard(this.normalizeIdCard(item.getIdCard()));
        student.setPhone(item.getPhone());
        student.setCollege(item.getCollege());
        student.setMajor(item.getMajor());
        student.setEduLevel(item.getEduLevel());
        student.setEnrollTime(item.getEnrollTime());
        student.setLeaveTime(item.getLeaveTime());
        student.setGrade(item.getGrade());
        student.setStudyType(item.getStudyType());
        student.setSchoolStatus(item.getSchoolStatus());
        student.setPhysicalExamResult(item.getPhysicalExamResult());
        student.setCounselor(item.getCounselor());
        student.setCounselorPhone(item.getCounselorPhone());
        // 操作人由底层审计框架自动填充，领域命令不传递 operatorId。
        return student;
    }

    /**
     * 写路径发布领域事件前解析当前操作人，禁止以 null 操作人落事件。
     *
     * @return 当前登录用户主键
     */
    private Long requireOperatorId() {
        Long operatorId = currentUser.getCurrentUserId();
        if (operatorId == null) {
            throw new BusinessException(ErrorCodeConstants.PATIENT_INVALID_ARGUMENT, "当前会话操作人不能为空");
        }
        return operatorId;
    }

}
