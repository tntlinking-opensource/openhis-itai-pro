package com.openhis.whale.mrms.application;

import com.openhis.whale.mrms.application.assembler.CaseRecordAssembler;
import com.openhis.whale.mrms.application.dto.CaseRecordDetailDto;
import com.openhis.whale.mrms.application.request.CaseRecordActionRequest;
import com.openhis.whale.mrms.domain.entity.CaseRecord;
import com.openhis.whale.mrms.domain.enums.CaseRecordTransitionAction;
import com.openhis.whale.mrms.domain.enums.WorkflowRole;
import com.openhis.whale.mrms.domain.service.CaseRecordManager;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/**
 * 病案室工作流 AppService → /api/app/caseRecordWorkflow/{methodName}
 */
@Service
@Validated
@RequiredArgsConstructor
public class CaseRecordWorkflowAppService {

    private final CaseRecordManager caseRecordManager;
    private final CaseRecordAssembler caseRecordAssembler;

    @Transactional(rollbackFor = Exception.class)
    public CaseRecordDetailDto receive(@Valid CaseRecordActionRequest request) {
        CaseRecord record = caseRecordManager.transition(request.getCaseRecordId(),
                CaseRecordTransitionAction.RECEIVE, WorkflowRole.RECORD_ROOM, null);
        return caseRecordAssembler.toDetail(record);
    }

    @Transactional(rollbackFor = Exception.class)
    public CaseRecordDetailDto returnToDoctor(@Valid CaseRecordActionRequest request) {
        CaseRecord record = caseRecordManager.transition(request.getCaseRecordId(),
                CaseRecordTransitionAction.RETURN, WorkflowRole.RECORD_ROOM, request.getReason());
        return caseRecordAssembler.toDetail(record);
    }
}
