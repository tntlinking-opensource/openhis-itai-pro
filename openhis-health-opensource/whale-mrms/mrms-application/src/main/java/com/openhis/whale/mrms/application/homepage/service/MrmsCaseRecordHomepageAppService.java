/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.application.homepage.service;

import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.mrms.application.homepage.dto.CaseRecordDetailDto;
import com.openhis.whale.mrms.application.homepage.dto.HomepageDto;
import com.openhis.whale.mrms.application.homepage.dto.HomepagePageDto;
import com.openhis.whale.mrms.application.homepage.dto.PdfGenerateResultDto;
import com.openhis.whale.mrms.application.homepage.request.GenerateHomepagePdfRequest;
import com.openhis.whale.mrms.application.homepage.request.LoadPageRequest;
import com.openhis.whale.mrms.application.homepage.request.ReceiveHomepageRequest;
import com.openhis.whale.mrms.application.homepage.request.RefreshAutoFillRequest;
import com.openhis.whale.mrms.application.homepage.request.ReturnToDoctorRequest;
import com.openhis.whale.mrms.application.homepage.request.SaveHomepageRequest;
import com.openhis.whale.mrms.application.homepage.request.SubmitHomepageRequest;
import com.openhis.whale.mrms.application.pdf.HomepageOpenPdfRenderer;
import com.openhis.whale.mrms.application.shared.assembler.CaseRecordAssembler;
import com.openhis.whale.mrms.application.shared.assembler.HomepageAssembler;
import com.openhis.whale.mrms.application.support.MrmsOperatorResolver;
import com.openhis.whale.mrms.domain.command.RefreshAutoFillCommand;
import com.openhis.whale.mrms.domain.command.SubmitCaseRecordCommand;
import com.openhis.whale.mrms.domain.command.TransitionCaseRecordCommand;
import com.openhis.whale.mrms.domain.entity.CaseRecord;
import com.openhis.whale.mrms.domain.entity.CaseRecordHomepage;
import com.openhis.whale.mrms.domain.enums.CaseRecordTransitionAction;
import com.openhis.whale.mrms.domain.enums.WorkflowRole;
import com.openhis.whale.mrms.domain.service.CaseRecordDomainService;
import com.openhis.whale.mrms.domain.service.CaseRecordHomepageDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 病案首页编辑 AppService → {@code POST /api/app/mrmsCaseRecordHomepage/*}
 */
@Service
@RequiredArgsConstructor
public class MrmsCaseRecordHomepageAppService {

    private final CaseRecordDomainService caseRecordDomainService;
    private final CaseRecordHomepageDomainService caseRecordHomepageDomainService;
    private final CaseRecordAssembler caseRecordAssembler;
    private final HomepageAssembler homepageAssembler;
    private final HomepageOpenPdfRenderer homepageOpenPdfRenderer;
    private final MrmsOperatorResolver operatorResolver;

    /**
     * 加载首页编辑页（主档 + 首页 + 子表）。
     *
     * @param request 请求
     * @return 页面 DTO
     */
    public HomepagePageDto loadPage(LoadPageRequest request) {
        if (request.getCaseRecordId() == null) {
            throw new ValidationException("病案主档主键不能为空");
        }
        CaseRecord record = caseRecordDomainService.requireById(request.getCaseRecordId());
        CaseRecordHomepage homepage = caseRecordHomepageDomainService.ensureHomepage(
                record.getId(), record.getTenantId(), operatorResolver.resolveOperatorIdOrNull());
        HomepagePageDto page = new HomepagePageDto();
        page.setCaseRecord(caseRecordAssembler.toDetail(record));
        page.setHomepage(homepageAssembler.toDto(
                homepage,
                caseRecordHomepageDomainService.listDiagnoses(record.getId()),
                caseRecordHomepageDomainService.listProcedures(record.getId())));
        return page;
    }

    /**
     * 保存首页标量与子表。
     *
     * @param request 保存请求
     * @return 首页 DTO
     */
    @Transactional(rollbackFor = Exception.class)
    public HomepageDto saveHomepage(SaveHomepageRequest request) {
        if (request.getCaseRecordId() == null) {
            throw new ValidationException("病案主档主键不能为空");
        }
        Long operatorId = operatorResolver.requireOperatorId();
        CaseRecordHomepage homepage = caseRecordDomainService.saveHomepage(
                homepageAssembler.toSaveCommand(request, operatorId));
        return homepageAssembler.toDto(
                homepage,
                caseRecordHomepageDomainService.listDiagnoses(request.getCaseRecordId()),
                caseRecordHomepageDomainService.listProcedures(request.getCaseRecordId()));
    }

    /**
     * 按段刷新 HIS 自动取值。
     *
     * @param request 刷新请求
     * @return 首页 DTO
     */
    @Transactional(rollbackFor = Exception.class)
    public HomepageDto refreshAutoFill(RefreshAutoFillRequest request) {
        if (request.getCaseRecordId() == null) {
            throw new ValidationException("病案主档主键不能为空");
        }
        if (request.getSections() == null || request.getSections().isEmpty()) {
            throw new ValidationException("刷新段不能为空");
        }
        RefreshAutoFillCommand command = new RefreshAutoFillCommand();
        command.setCaseRecordId(request.getCaseRecordId());
        command.setSections(request.getSections());
        command.setForceOverwrite(request.isForceOverwrite());
        command.setOperatorId(operatorResolver.requireOperatorId());
        CaseRecordHomepage homepage = caseRecordDomainService.refreshAutoFill(command);
        return homepageAssembler.toDto(
                homepage,
                caseRecordHomepageDomainService.listDiagnoses(request.getCaseRecordId()),
                caseRecordHomepageDomainService.listProcedures(request.getCaseRecordId()));
    }

    /**
     * 医生提交首页至病案室。
     *
     * @param request 提交请求
     * @return 主档详情
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecordDetailDto submit(SubmitHomepageRequest request) {
        if (request.getCaseRecordId() == null) {
            throw new ValidationException("病案主档主键不能为空");
        }
        SubmitCaseRecordCommand command = new SubmitCaseRecordCommand();
        command.setCaseRecordId(request.getCaseRecordId());
        command.setOperatorId(operatorResolver.requireOperatorId());
        CaseRecord record = caseRecordDomainService.submit(command);
        return caseRecordAssembler.toDetail(record);
    }

    /**
     * 病案室接收已提交病案。
     *
     * @param request 接收请求
     * @return 主档详情
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecordDetailDto receive(ReceiveHomepageRequest request) {
        if (request.getCaseRecordId() == null) {
            throw new ValidationException("病案主档主键不能为空");
        }
        TransitionCaseRecordCommand command = new TransitionCaseRecordCommand();
        command.setCaseRecordId(request.getCaseRecordId());
        command.setAction(CaseRecordTransitionAction.RECEIVE);
        command.setRole(WorkflowRole.RECORD_ROOM);
        command.setOperatorId(operatorResolver.requireOperatorId());
        CaseRecord record = caseRecordDomainService.transition(command);
        return caseRecordAssembler.toDetail(record);
    }

    /**
     * 病案室退回医生修改。
     *
     * @param request 退回请求
     * @return 主档详情
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecordDetailDto returnToDoctor(ReturnToDoctorRequest request) {
        if (request.getCaseRecordId() == null) {
            throw new ValidationException("病案主档主键不能为空");
        }
        if (trimToNull(request.getReturnReason()) == null) {
            throw new ValidationException("退回原因不能为空");
        }
        TransitionCaseRecordCommand command = new TransitionCaseRecordCommand();
        command.setCaseRecordId(request.getCaseRecordId());
        command.setAction(CaseRecordTransitionAction.RETURN);
        command.setRole(WorkflowRole.RECORD_ROOM);
        command.setReturnReason(request.getReturnReason());
        command.setOperatorId(operatorResolver.requireOperatorId());
        CaseRecord record = caseRecordDomainService.transition(command);
        return caseRecordAssembler.toDetail(record);
    }

    /**
     * 生成首页 PDF 预览。
     *
     * @param request 请求
     * @return PDF 结果
     */
    public PdfGenerateResultDto generateHomepagePdf(GenerateHomepagePdfRequest request) {
        if (request.getCaseRecordId() == null) {
            throw new ValidationException("病案主档主键不能为空");
        }
        CaseRecord record = caseRecordDomainService.requireById(request.getCaseRecordId());
        CaseRecordHomepage homepage = caseRecordHomepageDomainService.ensureHomepage(
                record.getId(), record.getTenantId(), operatorResolver.resolveOperatorIdOrNull());
        HomepageDto dto = homepageAssembler.toDto(
                homepage,
                caseRecordHomepageDomainService.listDiagnoses(record.getId()),
                caseRecordHomepageDomainService.listProcedures(record.getId()));
        return homepageOpenPdfRenderer.render(dto);
    }
}
