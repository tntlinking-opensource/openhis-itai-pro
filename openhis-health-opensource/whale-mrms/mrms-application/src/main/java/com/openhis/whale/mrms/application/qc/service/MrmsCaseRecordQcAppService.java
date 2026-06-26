/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.application.qc.service;

import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.mrms.application.homepage.dto.CaseRecordDetailDto;
import com.openhis.whale.mrms.application.qc.request.ArchiveCaseRecordRequest;
import com.openhis.whale.mrms.application.qc.request.StartQcRequest;
import com.openhis.whale.mrms.application.shared.assembler.CaseRecordAssembler;
import com.openhis.whale.mrms.application.support.MrmsOperatorResolver;
import com.openhis.whale.mrms.domain.command.TransitionCaseRecordCommand;
import com.openhis.whale.mrms.domain.entity.CaseRecord;
import com.openhis.whale.mrms.domain.enums.CaseRecordTransitionAction;
import com.openhis.whale.mrms.domain.enums.WorkflowRole;
import com.openhis.whale.mrms.domain.service.CaseRecordDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 病案质控与归档 AppService → {@code POST /api/app/mrmsCaseRecordQc/*}
 */
@Service
@RequiredArgsConstructor
public class MrmsCaseRecordQcAppService {

    private final CaseRecordDomainService caseRecordDomainService;
    private final CaseRecordAssembler caseRecordAssembler;
    private final MrmsOperatorResolver operatorResolver;

    /**
     * 病案室开始质控（received → qcing）。
     *
     * @param request 请求
     * @return 主档详情
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecordDetailDto startQc(StartQcRequest request) {
        if (request.getCaseRecordId() == null) {
            throw new ValidationException("病案主档主键不能为空");
        }
        TransitionCaseRecordCommand command = new TransitionCaseRecordCommand();
        command.setCaseRecordId(request.getCaseRecordId());
        command.setAction(CaseRecordTransitionAction.START_QC);
        command.setRole(WorkflowRole.RECORD_ROOM);
        command.setOperatorId(operatorResolver.requireOperatorId());
        CaseRecord record = caseRecordDomainService.transition(command);
        return caseRecordAssembler.toDetail(record);
    }

    /**
     * 病案室归档（qcing → archived，冻结首页）。
     *
     * @param request 请求
     * @return 主档详情
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecordDetailDto archive(ArchiveCaseRecordRequest request) {
        if (request.getCaseRecordId() == null) {
            throw new ValidationException("病案主档主键不能为空");
        }
        TransitionCaseRecordCommand command = new TransitionCaseRecordCommand();
        command.setCaseRecordId(request.getCaseRecordId());
        command.setAction(CaseRecordTransitionAction.ARCHIVE);
        command.setRole(WorkflowRole.RECORD_ROOM);
        command.setQualityGradeCode(request.getQualityGradeCode());
        command.setOperatorId(operatorResolver.requireOperatorId());
        CaseRecord record = caseRecordDomainService.transition(command);
        return caseRecordAssembler.toDetail(record);
    }
}
