package com.openhis.whale.mrms.application;

import com.openhis.whale.mrms.application.assembler.HomepageAssembler;
import com.openhis.whale.mrms.application.dto.DiagnosisLineDto;
import com.openhis.whale.mrms.application.dto.IcdItemDto;
import com.openhis.whale.mrms.application.dto.ProcedureLineDto;
import com.openhis.whale.mrms.application.request.SaveDiagnosisLinesRequest;
import com.openhis.whale.mrms.application.request.SaveHomepageRequest;
import com.openhis.whale.mrms.application.request.SaveProcedureLinesRequest;
import com.openhis.whale.mrms.application.request.SearchIcdRequest;
import com.openhis.whale.mrms.domain.entity.CaseRecord;
import com.openhis.whale.mrms.domain.entity.HomepageDiagnosis;
import com.openhis.whale.mrms.domain.entity.HomepageProcedure;
import com.openhis.whale.mrms.domain.model.HomepageSaveCommand.DiagnosisLineCommand;
import com.openhis.whale.mrms.domain.model.HomepageSaveCommand.ProcedureLineCommand;
import com.openhis.whale.mrms.port.IcdSearchPort;
import com.openhis.whale.mrms.domain.service.CaseRecordHomepageManager;
import com.openhis.whale.mrms.domain.service.CaseRecordManager;
import com.openhis.whale.mrms.domain.service.CaseRecordStatusValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * 病案编码 AppService → /api/app/caseRecordCoding/{methodName}
 */
@Service
@Validated
@RequiredArgsConstructor
public class CaseRecordCodingAppService {

    private final IcdSearchPort icdSearchPort;
    private final CaseRecordManager caseRecordManager;
    private final CaseRecordHomepageManager homepageManager;
    private final CaseRecordStatusValidator statusValidator;
    private final HomepageAssembler homepageAssembler;

    public List<IcdItemDto> searchIcd10(@Valid SearchIcdRequest request) {
        int limit = request.getLimit() != null && request.getLimit() > 0 ? request.getLimit() : 20;
        return icdSearchPort.searchIcd10(request.getKeyword(), limit).stream()
                .map(homepageAssembler::toIcdDto)
                .toList();
    }

    public List<IcdItemDto> searchIcd9Cm3(@Valid SearchIcdRequest request) {
        int limit = request.getLimit() != null && request.getLimit() > 0 ? request.getLimit() : 20;
        return icdSearchPort.searchIcd9Cm3(request.getKeyword(), limit).stream()
                .map(homepageAssembler::toIcdDto)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public List<DiagnosisLineDto> saveDiagnosisLines(@Valid SaveDiagnosisLinesRequest request) {
        CaseRecord caseRecord = caseRecordManager.requireById(request.getCaseRecordId());
        statusValidator.assertEditableForDoctor(caseRecord);
        List<DiagnosisLineCommand> lines = new ArrayList<>();
        if (request.getLines() != null) {
            for (SaveHomepageRequest.DiagnosisLineRequest line : request.getLines()) {
                DiagnosisLineCommand cmd = new DiagnosisLineCommand();
                cmd.setDiagTypeCode(request.getDiagTypeCode());
                cmd.setSortNo(line.getSortNo() == null ? 0 : line.getSortNo());
                cmd.setDiagCode(line.getDiagCode());
                cmd.setDiagName(line.getDiagName());
                cmd.setAdmCondCode(line.getAdmCondCode());
                lines.add(cmd);
            }
        }
        List<HomepageDiagnosis> saved = homepageManager.replaceDiagnosesByType(
                caseRecord.getId(), caseRecord.getTenantId(), request.getDiagTypeCode(), lines);
        return saved.stream().map(homepageAssembler::toDiagnosisDto).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public List<ProcedureLineDto> saveProcedureLines(@Valid SaveProcedureLinesRequest request) {
        CaseRecord caseRecord = caseRecordManager.requireById(request.getCaseRecordId());
        statusValidator.assertEditableForDoctor(caseRecord);
        List<ProcedureLineCommand> lines = new ArrayList<>();
        if (request.getLines() != null) {
            for (SaveHomepageRequest.ProcedureLineRequest line : request.getLines()) {
                ProcedureLineCommand cmd = new ProcedureLineCommand();
                cmd.setSortNo(line.getSortNo() == null ? 0 : line.getSortNo());
                cmd.setOprnOprtCode(line.getOprnOprtCode());
                cmd.setOprnOprtName(line.getOprnOprtName());
                cmd.setOprnDate(line.getOprnDate());
                cmd.setOprnLevelCode(line.getOprnLevelCode());
                cmd.setOprnIncisionCode(line.getOprnIncisionCode());
                cmd.setHealGradeCode(line.getHealGradeCode());
                cmd.setAnstWayCode(line.getAnstWayCode());
                lines.add(cmd);
            }
        }
        List<HomepageProcedure> saved = homepageManager.replaceProcedures(
                caseRecord.getId(), caseRecord.getTenantId(), lines);
        return saved.stream().map(homepageAssembler::toProcedureDto).toList();
    }
}
