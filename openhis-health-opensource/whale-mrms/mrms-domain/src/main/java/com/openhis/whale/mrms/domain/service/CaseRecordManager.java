package com.openhis.whale.mrms.domain.service;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.EntityNotFoundException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import com.openhis.whale.mrms.domain.entity.CaseRecord;
import com.openhis.whale.mrms.domain.enums.CaseRecordStatus;
import com.openhis.whale.mrms.domain.enums.CaseRecordTransitionAction;
import com.openhis.whale.mrms.domain.enums.WorkflowRole;
import com.openhis.whale.mrms.port.PatientEncounterPort;
import com.openhis.whale.mrms.port.model.EncounterSnapshot;
import com.openhis.whale.mrms.port.model.PatientSnapshot;
import com.openhis.whale.mrms.domain.repository.CaseRecordRepository;
import com.openhis.whale.mrms.domain.support.MrmsErrorCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 病案主档领域服务：创建、状态迁移、摘要冗余。
 */
@Service
@RequiredArgsConstructor
public class CaseRecordManager {

    private final CaseRecordRepository caseRecordRepository;
    private final MedcasnoGenerator medcasnoGenerator;
    private final PatientEncounterPort patientEncounterPort;
    private final CaseRecordStatusValidator statusValidator;
    private final CaseRecordHomepageManager homepageManager;

    public Optional<CaseRecord> findById(Long id) {
        return caseRecordRepository.findByIdAndIsDeletedFalse(id);
    }

    public CaseRecord requireById(Long id) {
        return findById(id).orElseThrow(() -> new EntityNotFoundException("病案主档不存在: " + id));
    }

    public Optional<CaseRecord> findByEncounterId(Long encounterId) {
        Long tenantId = TenantUtils.getCurrentTenantId();
        return caseRecordRepository.findByTenantIdAndEncounterIdAndIsDeletedFalse(tenantId, encounterId);
    }

    @Transactional(rollbackFor = Exception.class)
    public CaseRecord createIfAbsent(Long encounterId) {
        Long tenantId = TenantUtils.getCurrentTenantId();
        Optional<CaseRecord> existing = caseRecordRepository.findByTenantIdAndEncounterIdAndIsDeletedFalse(tenantId, encounterId);
        if (existing.isPresent()) {
            return existing.get();
        }
        EncounterSnapshot encounter = patientEncounterPort.findEncounter(encounterId)
                .orElseThrow(() -> new BusinessException(MrmsErrorCodes.CASE_RECORD_NOT_FOUND, "就诊不存在: " + encounterId));
        PatientSnapshot patient = patientEncounterPort.findPatient(encounter.getPatientId())
                .orElseThrow(() -> new BusinessException(MrmsErrorCodes.CASE_RECORD_NOT_FOUND, "患者不存在: " + encounter.getPatientId()));

        CaseRecord record = new CaseRecord();
        record.setTenantId(tenantId);
        record.setEncounterId(encounterId);
        record.setPatientId(encounter.getPatientId());
        record.setMedcasno(medcasnoGenerator.nextMedcasno());
        record.setStatusCode(CaseRecordStatus.DRAFT.getCode());
        applySummary(record, patient, encounter);
        record.setPatnIptCnt(patientEncounterPort.countInpatientAdmissions(encounter.getPatientId(), encounterId));
        CaseRecord saved = caseRecordRepository.save(record);
        homepageManager.ensureHomepage(saved.getId(), tenantId);
        return saved;
    }

    @Transactional(rollbackFor = Exception.class)
    public CaseRecord transition(Long caseRecordId, CaseRecordTransitionAction action, WorkflowRole role, String reason) {
        CaseRecord record = requireById(caseRecordId);
        statusValidator.assertTransitionAllowed(record, action, role);
        CaseRecordStatus target = resolveTargetStatus(action);
        record.setStatusCode(target.getCode());
        if (action == CaseRecordTransitionAction.RETURN) {
            if (!StringUtils.hasText(reason)) {
                throw new BusinessException(MrmsErrorCodes.VALIDATION_FAILED, "退回必须填写原因");
            }
            record.setReturnReason(reason);
        }
        if (action == CaseRecordTransitionAction.ARCHIVE) {
            record.setArchivedAt(LocalDateTime.now());
            homepageManager.freeze(caseRecordId);
        }
        if (action == CaseRecordTransitionAction.UNARCHIVE) {
            record.setArchivedAt(null);
            record.setArchivedBy(null);
            homepageManager.unfreeze(caseRecordId);
        }
        return caseRecordRepository.save(record);
    }

    @Transactional(rollbackFor = Exception.class)
    public void refreshSummary(Long caseRecordId) {
        CaseRecord record = requireById(caseRecordId);
        EncounterSnapshot encounter = patientEncounterPort.findEncounter(record.getEncounterId()).orElse(null);
        if (encounter == null) {
            return;
        }
        PatientSnapshot patient = patientEncounterPort.findPatient(record.getPatientId()).orElse(null);
        if (patient != null) {
            applySummary(record, patient, encounter);
        } else {
            applyEncounterOnly(record, encounter);
        }
        caseRecordRepository.save(record);
    }

    private void applySummary(CaseRecord record, PatientSnapshot patient, EncounterSnapshot encounter) {
        record.setPatientName(patient.getName());
        record.setGenderCode(patient.getGenderCode());
        applyEncounterOnly(record, encounter);
    }

    private void applyEncounterOnly(CaseRecord record, EncounterSnapshot encounter) {
        record.setInpatientNo(encounter.getInpatientNo());
        record.setAdmissionAt(encounter.getAdmissionAt());
        record.setDischargeAt(encounter.getDischargeAt());
        record.setDeptCode(encounter.getDeptCode());
        record.setDeptName(encounter.getDeptName());
        record.setAttendingPractitionerId(encounter.getAttendingPractitionerId());
        record.setAttendingPractitionerName(encounter.getAttendingPractitionerName());
    }

    private CaseRecordStatus resolveTargetStatus(CaseRecordTransitionAction action) {
        return switch (action) {
            case SUBMIT -> CaseRecordStatus.SUBMITTED;
            case RECEIVE -> CaseRecordStatus.RECEIVED;
            case RETURN -> CaseRecordStatus.RETURNED;
            case START_QC -> CaseRecordStatus.QCING;
            case ARCHIVE -> CaseRecordStatus.ARCHIVED;
            case UNARCHIVE -> CaseRecordStatus.RECEIVED;
        };
    }
}
