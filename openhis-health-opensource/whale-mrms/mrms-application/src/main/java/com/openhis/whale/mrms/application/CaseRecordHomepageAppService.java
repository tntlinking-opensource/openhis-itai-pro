package com.openhis.whale.mrms.application;

import com.openhis.whale.mrms.application.assembler.CaseRecordAssembler;
import com.openhis.whale.mrms.application.assembler.HomepageAssembler;
import com.openhis.whale.mrms.application.dto.CaseRecordDetailDto;
import com.openhis.whale.mrms.application.dto.HomepageDto;
import com.openhis.whale.mrms.application.request.RefreshAutoFillRequest;
import com.openhis.whale.mrms.application.request.SaveHomepageRequest;
import com.openhis.whale.mrms.application.request.SubmitHomepageRequest;
import com.openhis.whale.mrms.application.support.HomepageRequestMapper;
import com.openhis.whale.mrms.application.support.HomepageSubmitValidator;
import com.openhis.whale.mrms.domain.entity.CaseRecord;
import com.openhis.whale.mrms.domain.entity.CaseRecordHomepage;
import com.openhis.whale.mrms.domain.enums.CaseRecordTransitionAction;
import com.openhis.whale.mrms.domain.enums.WorkflowRole;
import com.openhis.whale.mrms.domain.model.HomepageSaveCommand;
import com.openhis.whale.mrms.domain.service.CaseRecordHomepageManager;
import com.openhis.whale.mrms.domain.service.CaseRecordManager;
import com.openhis.whale.mrms.domain.service.CaseRecordStatusValidator;
import com.openhis.whale.mrms.domain.service.HomepageAutoFillMerger;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import com.openhis.whale.mrms.application.request.LongValueRequest;

/**
 * 病案首页 AppService → /api/app/caseRecordHomepage/{methodName}
 */
@Service
@Validated
@RequiredArgsConstructor
public class CaseRecordHomepageAppService {

    private final CaseRecordManager caseRecordManager;
    private final CaseRecordHomepageManager homepageManager;
    private final CaseRecordStatusValidator statusValidator;
    private final HomepageAutoFillMerger autoFillMerger;
    private final HomepageRequestMapper homepageRequestMapper;
    private final HomepageSubmitValidator submitValidator;
    private final CaseRecordAssembler caseRecordAssembler;
    private final HomepageAssembler homepageAssembler;

    public HomepageDto getHomepage(@Valid LongValueRequest request) {
        return loadHomepageDto(request.getValue());
    }

    @Transactional(rollbackFor = Exception.class)
    public HomepageDto saveHomepage(@Valid SaveHomepageRequest request) {
        CaseRecord caseRecord = caseRecordManager.requireById(request.getCaseRecordId());
        statusValidator.assertEditableForDoctor(caseRecord);
        HomepageSaveCommand command = homepageRequestMapper.toCommand(request);
        CaseRecordHomepage homepage = homepageManager.save(command);
        return homepageAssembler.toDto(homepage,
                homepageManager.listDiagnoses(homepage.getCaseRecordId()),
                homepageManager.listProcedures(homepage.getCaseRecordId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public HomepageDto refreshAutoFill(@Valid RefreshAutoFillRequest request) {
        CaseRecord caseRecord = caseRecordManager.requireById(request.getCaseRecordId());
        statusValidator.assertEditableForDoctor(caseRecord);
        CaseRecordHomepage homepage = autoFillMerger.merge(caseRecord, request.getSections(), request.isForceOverwrite());
        caseRecordManager.refreshSummary(caseRecord.getId());
        return homepageAssembler.toDto(homepage,
                homepageManager.listDiagnoses(homepage.getCaseRecordId()),
                homepageManager.listProcedures(homepage.getCaseRecordId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public CaseRecordDetailDto submit(@Valid SubmitHomepageRequest request) {
        submitValidator.validate(request.getCaseRecordId());
        CaseRecord record = caseRecordManager.transition(request.getCaseRecordId(),
                CaseRecordTransitionAction.SUBMIT, WorkflowRole.DOCTOR, null);
        return caseRecordAssembler.toDetail(record);
    }

    private HomepageDto loadHomepageDto(Long caseRecordId) {
        CaseRecord caseRecord = caseRecordManager.requireById(caseRecordId);
        CaseRecordHomepage homepage = homepageManager.ensureHomepage(caseRecordId, caseRecord.getTenantId());
        return homepageAssembler.toDto(homepage,
                homepageManager.listDiagnoses(caseRecordId),
                homepageManager.listProcedures(caseRecordId));
    }
}
