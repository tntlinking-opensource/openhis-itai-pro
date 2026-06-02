package com.openhis.whale.mrms.domain.service;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.EntityNotFoundException;
import com.openhis.whale.mrms.domain.entity.CaseRecordHomepage;
import com.openhis.whale.mrms.domain.entity.HomepageDiagnosis;
import com.openhis.whale.mrms.domain.entity.HomepageProcedure;
import com.openhis.whale.mrms.domain.model.HomepageSaveCommand;
import com.openhis.whale.mrms.domain.model.HomepageSaveCommand.DiagnosisLineCommand;
import com.openhis.whale.mrms.domain.model.HomepageSaveCommand.ProcedureLineCommand;
import com.openhis.whale.mrms.domain.repository.CaseRecordHomepageRepository;
import com.openhis.whale.mrms.domain.repository.HomepageDiagnosisRepository;
import com.openhis.whale.mrms.domain.repository.HomepageProcedureRepository;
import com.openhis.whale.mrms.domain.support.MrmsErrorCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 病案首页保存、版本与冻结。
 */
@Service
@RequiredArgsConstructor
public class CaseRecordHomepageManager {

    private final CaseRecordHomepageRepository homepageRepository;
    private final HomepageDiagnosisRepository diagnosisRepository;
    private final HomepageProcedureRepository procedureRepository;
    private final FieldSourcesJsonHelper fieldSourcesJsonHelper;

    public CaseRecordHomepage requireByCaseRecordId(Long caseRecordId) {
        return homepageRepository.findByCaseRecordIdAndIsDeletedFalse(caseRecordId)
                .orElseThrow(() -> new EntityNotFoundException("病案首页不存在: " + caseRecordId));
    }

    @Transactional(rollbackFor = Exception.class)
    public CaseRecordHomepage ensureHomepage(Long caseRecordId, Long tenantId) {
        return homepageRepository.findByCaseRecordIdAndIsDeletedFalse(caseRecordId)
                .orElseGet(() -> {
                    CaseRecordHomepage homepage = new CaseRecordHomepage();
                    homepage.setTenantId(tenantId);
                    homepage.setCaseRecordId(caseRecordId);
                    homepage.setVersionNo(1);
                    homepage.setFrozen(false);
                    return homepageRepository.save(homepage);
                });
    }

    @Transactional(rollbackFor = Exception.class)
    public CaseRecordHomepage save(HomepageSaveCommand command) {
        CaseRecordHomepage homepage = requireByCaseRecordId(command.getCaseRecordId());
        if (Boolean.TRUE.equals(homepage.getFrozen())) {
            throw new BusinessException(MrmsErrorCodes.HOMEPAGE_FROZEN, "病案首页已冻结，禁止保存");
        }
        applyScalars(homepage, command);
        if (command.getFieldSourcesJson() != null) {
            homepage.setFieldSourcesJson(command.getFieldSourcesJson());
        }
        if (command.getExtensionJson() != null) {
            homepage.setExtensionJson(command.getExtensionJson());
        }
        homepage.setVersionNo(homepage.getVersionNo() + 1);
        CaseRecordHomepage saved = homepageRepository.save(homepage);
        if (command.getDiagnoses() != null) {
            replaceDiagnoses(saved.getCaseRecordId(), saved.getTenantId(), command.getDiagnoses());
        }
        if (command.getProcedures() != null) {
            replaceProcedures(saved.getCaseRecordId(), saved.getTenantId(), command.getProcedures());
        }
        return saved;
    }

    @Transactional(rollbackFor = Exception.class)
    public CaseRecordHomepage saveScalarsOnly(CaseRecordHomepage homepage) {
        homepage.setVersionNo(homepage.getVersionNo() + 1);
        return homepageRepository.save(homepage);
    }

    @Transactional(rollbackFor = Exception.class)
    public void freeze(Long caseRecordId) {
        CaseRecordHomepage homepage = requireByCaseRecordId(caseRecordId);
        homepage.setFrozen(true);
        homepageRepository.save(homepage);
    }

    @Transactional(rollbackFor = Exception.class)
    public void unfreeze(Long caseRecordId) {
        CaseRecordHomepage homepage = requireByCaseRecordId(caseRecordId);
        homepage.setFrozen(false);
        homepageRepository.save(homepage);
    }

    public List<HomepageDiagnosis> listDiagnoses(Long caseRecordId) {
        return diagnosisRepository.findAllByCaseRecordIdAndIsDeletedFalseOrderByDiagTypeCodeAscSortNoAsc(caseRecordId);
    }

    public List<HomepageProcedure> listProcedures(Long caseRecordId) {
        return procedureRepository.findAllByCaseRecordIdAndIsDeletedFalseOrderBySortNoAsc(caseRecordId);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<HomepageDiagnosis> replaceDiagnosesByType(Long caseRecordId, Long tenantId, String diagTypeCode,
            List<DiagnosisLineCommand> lines) {
        List<HomepageDiagnosis> existing = diagnosisRepository
                .findAllByCaseRecordIdAndDiagTypeCodeAndIsDeletedFalseOrderBySortNoAsc(caseRecordId, diagTypeCode);
        LocalDateTime now = LocalDateTime.now();
        for (HomepageDiagnosis row : existing) {
            row.setIsDeleted(true);
            row.setUpdatedAt(now);
        }
        diagnosisRepository.saveAll(existing);
        List<HomepageDiagnosis> saved = new ArrayList<>();
        if (lines == null) {
            return saved;
        }
        for (DiagnosisLineCommand line : lines) {
            HomepageDiagnosis row = new HomepageDiagnosis();
            row.setCaseRecordId(caseRecordId);
            row.setTenantId(tenantId);
            row.setDiagTypeCode(diagTypeCode);
            row.setSortNo(line.getSortNo());
            row.setDiagCode(line.getDiagCode());
            row.setDiagName(line.getDiagName());
            row.setAdmCondCode(line.getAdmCondCode());
            row.setCreatedAt(now);
            row.setIsDeleted(false);
            saved.add(row);
        }
        return diagnosisRepository.saveAll(saved);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<HomepageProcedure> replaceProcedures(Long caseRecordId, Long tenantId, List<ProcedureLineCommand> lines) {
        List<HomepageProcedure> existing = procedureRepository.findAllByCaseRecordIdAndIsDeletedFalseOrderBySortNoAsc(caseRecordId);
        LocalDateTime now = LocalDateTime.now();
        for (HomepageProcedure row : existing) {
            row.setIsDeleted(true);
            row.setUpdatedAt(now);
        }
        procedureRepository.saveAll(existing);
        List<HomepageProcedure> saved = new ArrayList<>();
        if (lines == null) {
            return saved;
        }
        for (ProcedureLineCommand line : lines) {
            HomepageProcedure row = new HomepageProcedure();
            row.setCaseRecordId(caseRecordId);
            row.setTenantId(tenantId);
            row.setSortNo(line.getSortNo());
            row.setOprnOprtCode(line.getOprnOprtCode());
            row.setOprnOprtName(line.getOprnOprtName());
            row.setOprnDate(line.getOprnDate());
            row.setOprnLevelCode(line.getOprnLevelCode());
            row.setOprnIncisionCode(line.getOprnIncisionCode());
            row.setHealGradeCode(line.getHealGradeCode());
            row.setAnstWayCode(line.getAnstWayCode());
            row.setCreatedAt(now);
            row.setIsDeleted(false);
            saved.add(row);
        }
        return procedureRepository.saveAll(saved);
    }

    private void replaceDiagnoses(Long caseRecordId, Long tenantId, List<DiagnosisLineCommand> lines) {
        Map<String, List<DiagnosisLineCommand>> grouped = lines.stream()
                .collect(Collectors.groupingBy(DiagnosisLineCommand::getDiagTypeCode));
        Set<String> types = new HashSet<>(grouped.keySet());
        List<HomepageDiagnosis> existing = diagnosisRepository
                .findAllByCaseRecordIdAndIsDeletedFalseOrderByDiagTypeCodeAscSortNoAsc(caseRecordId);
        for (String type : existing.stream().map(HomepageDiagnosis::getDiagTypeCode).collect(Collectors.toSet())) {
            types.add(type);
        }
        for (String type : types) {
            replaceDiagnosesByType(caseRecordId, tenantId, type, grouped.get(type));
        }
    }

    private void applyScalars(CaseRecordHomepage homepage, HomepageSaveCommand command) {
        if (command.getPsnName() != null) {
            homepage.setPsnName(command.getPsnName());
        }
        if (command.getGendCode() != null) {
            homepage.setGendCode(command.getGendCode());
        }
        if (command.getBrdy() != null) {
            homepage.setBrdy(command.getBrdy());
        }
        if (command.getCertno() != null) {
            homepage.setCertno(command.getCertno());
        }
        if (command.getNtlyCode() != null) {
            homepage.setNtlyCode(command.getNtlyCode());
        }
        if (command.getNatyCode() != null) {
            homepage.setNatyCode(command.getNatyCode());
        }
        if (command.getPrfsCode() != null) {
            homepage.setPrfsCode(command.getPrfsCode());
        }
        if (command.getMrgStasCode() != null) {
            homepage.setMrgStasCode(command.getMrgStasCode());
        }
        if (command.getCurrAddr() != null) {
            homepage.setCurrAddr(command.getCurrAddr());
        }
        if (command.getPsnTel() != null) {
            homepage.setPsnTel(command.getPsnTel());
        }
        if (command.getConerName() != null) {
            homepage.setConerName(command.getConerName());
        }
        if (command.getConerTel() != null) {
            homepage.setConerTel(command.getConerTel());
        }
        if (command.getConerAddr() != null) {
            homepage.setConerAddr(command.getConerAddr());
        }
        if (command.getIptNo() != null) {
            homepage.setIptNo(command.getIptNo());
        }
        if (command.getAdmWayCode() != null) {
            homepage.setAdmWayCode(command.getAdmWayCode());
        }
        if (command.getAdmCatyCode() != null) {
            homepage.setAdmCatyCode(command.getAdmCatyCode());
        }
        if (command.getAdmCatyName() != null) {
            homepage.setAdmCatyName(command.getAdmCatyName());
        }
        if (command.getDscgCatyCode() != null) {
            homepage.setDscgCatyCode(command.getDscgCatyCode());
        }
        if (command.getDscgCatyName() != null) {
            homepage.setDscgCatyName(command.getDscgCatyName());
        }
        if (command.getAdmTime() != null) {
            homepage.setAdmTime(command.getAdmTime());
        }
        if (command.getDscgTime() != null) {
            homepage.setDscgTime(command.getDscgTime());
        }
        if (command.getActIptDays() != null) {
            homepage.setActIptDays(command.getActIptDays());
        }
        if (command.getDscgWayCode() != null) {
            homepage.setDscgWayCode(command.getDscgWayCode());
        }
        if (command.getTransferDeptSummary() != null) {
            homepage.setTransferDeptSummary(command.getTransferDeptSummary());
        }
        if (command.getDeptDirectorName() != null) {
            homepage.setDeptDirectorName(command.getDeptDirectorName());
        }
        if (command.getChfdrName() != null) {
            homepage.setChfdrName(command.getChfdrName());
        }
        if (command.getAtddrName() != null) {
            homepage.setAtddrName(command.getAtddrName());
        }
        if (command.getIptDrName() != null) {
            homepage.setIptDrName(command.getIptDrName());
        }
        if (command.getRespNursName() != null) {
            homepage.setRespNursName(command.getRespNursName());
        }
    }
}
