/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.mrms.homepage.service;

import com.openhis.health.application.mrms.homepage.dto.CaseRecordDetailDto;
import com.openhis.health.application.mrms.homepage.dto.HomepageDto;
import com.openhis.health.application.mrms.homepage.dto.HomepagePageDto;
import com.openhis.health.application.mrms.homepage.dto.PdfGenerateResultDto;
import com.openhis.health.application.mrms.homepage.mapper.CaseRecordDetailMapper;
import com.openhis.health.application.mrms.homepage.mapper.HomepageMapper;
import com.openhis.health.application.mrms.homepage.request.GenerateHomepagePdfRequest;
import com.openhis.health.application.mrms.homepage.request.LoadPageRequest;
import com.openhis.health.application.mrms.homepage.request.ReceiveHomepageRequest;
import com.openhis.health.application.mrms.homepage.request.RefreshAutoFillRequest;
import com.openhis.health.application.mrms.homepage.request.ReturnToDoctorRequest;
import com.openhis.health.application.mrms.homepage.request.SaveHomepageRequest;
import com.openhis.health.application.mrms.homepage.request.SubmitHomepageRequest;
import com.openhis.health.application.mrms.homepage.support.HomepageOpenPdfRenderer;
import com.openhis.health.domain.mrms.caserecord.command.RefreshAutoFillCommand;
import com.openhis.health.domain.mrms.caserecord.command.SubmitCaseRecordCommand;
import com.openhis.health.domain.mrms.caserecord.command.TransitionCaseRecordCommand;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecord;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecordHomepage;
import com.openhis.health.domain.mrms.caserecord.service.CaseRecordDomainService;
import com.openhis.health.domain.mrms.caserecord.service.CaseRecordHomepageDomainService;
import com.openhis.health.domain.share.enums.CaseRecordTransitionAction;
import com.openhis.health.domain.share.enums.WorkflowRole;
import com.openhis.health.module.common.validator.MrmsOperatorResolver;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 病案首页编辑应用服务（IF-MR-Homepage）。
 *
 * <p>路由前缀 {@code POST /api/app/mrmsCaseRecordHomepage/*}。负责首页加载/保存、HIS 自动取值、
 * 工作流提交/接收/退回及 PDF 预览；写路径经 {@link CaseRecordDomainService} 与领域命令。</p>
 */
@Service
@RequiredArgsConstructor
public class MrmsCaseRecordHomepageAppService {

    private final CaseRecordDomainService caseRecordDomainService;
    private final CaseRecordHomepageDomainService caseRecordHomepageDomainService;
    private final CaseRecordDetailMapper caseRecordDetailMapper;
    private final HomepageMapper homepageMapper;
    private final HomepageOpenPdfRenderer homepageOpenPdfRenderer;
    private final MrmsOperatorResolver operatorResolver;

    /**
     * 加载首页编辑页（主档 + 首页 + 诊断/手术子表）。
     *
     * @param request 含病案主档主键
     * @return 页面聚合 DTO
     */
    public HomepagePageDto loadPage(LoadPageRequest request) {
        // Gate：病案主键必填，否则无法定位编辑对象
        if (request.getCaseRecordId() == null) {
            throw new ValidationException("病案主档主键不能为空");
        }
        // Pre：加载主档；首页不存在则懒创建空壳
        CaseRecord record = caseRecordDomainService.requireById(request.getCaseRecordId());
        CaseRecordHomepage homepage = caseRecordHomepageDomainService.ensureHomepage(
                record.getId(), record.getTenantId(), operatorResolver.resolveOperatorIdOrNull());
        // Asm：映射主档详情与首页 DTO
        HomepagePageDto page = new HomepagePageDto();
        page.setCaseRecord(caseRecordDetailMapper.toDetail(record));
        page.setHomepage(homepageMapper.toDto(
                homepage,
                caseRecordHomepageDomainService.listDiagnoses(record.getId()),
                caseRecordHomepageDomainService.listProcedures(record.getId())));
        return page;
    }

    /**
     * 保存首页标量、扩展 JSON 与诊断/手术子表。
     *
     * @param request 保存请求
     * @return 保存后的首页 DTO
     */
    @Transactional(rollbackFor = Exception.class)
    public HomepageDto saveHomepage(SaveHomepageRequest request) {
        // Gate：病案主键与登录操作人必填
        if (request.getCaseRecordId() == null) {
            throw new ValidationException("病案主档主键不能为空");
        }
        Long operatorId = operatorResolver.requireOperatorId();
        // Pre：HTTP 请求转领域保存命令
        // Cmd：领域校验可编辑态并持久化首页与子表
        CaseRecordHomepage homepage = caseRecordDomainService.saveHomepage(
                homepageMapper.toSaveCommand(request, operatorId));
        // Asm：重新加载子表并映射出参
        return homepageMapper.toDto(
                homepage,
                caseRecordHomepageDomainService.listDiagnoses(request.getCaseRecordId()),
                caseRecordHomepageDomainService.listProcedures(request.getCaseRecordId()));
    }

    /**
     * 按选定段从 HIS 刷新自动取值字段。
     *
     * @param request 刷新段列表与是否强制覆盖
     * @return 刷新后的首页 DTO
     */
    @Transactional(rollbackFor = Exception.class)
    public HomepageDto refreshAutoFill(RefreshAutoFillRequest request) {
        // Gate：病案主键与刷新段非空
        if (request.getCaseRecordId() == null) {
            throw new ValidationException("病案主档主键不能为空");
        }
        if (request.getSections() == null || request.getSections().isEmpty()) {
            // 未指定刷新段则无法合并 HIS 快照
            throw new ValidationException("刷新段不能为空");
        }
        // Pre：组装刷新命令
        RefreshAutoFillCommand command = new RefreshAutoFillCommand();
        command.setCaseRecordId(request.getCaseRecordId());
        command.setSections(request.getSections());
        command.setForceOverwrite(request.isForceOverwrite());
        command.setOperatorId(operatorResolver.requireOperatorId());
        // Cmd：领域合并 HIS 快照写回首页
        CaseRecordHomepage homepage = caseRecordDomainService.refreshAutoFill(command);
        // Asm：映射出参
        return homepageMapper.toDto(
                homepage,
                caseRecordHomepageDomainService.listDiagnoses(request.getCaseRecordId()),
                caseRecordHomepageDomainService.listProcedures(request.getCaseRecordId()));
    }

    /**
     * 住院医师提交首页至病案室（draft → submitted）。
     *
     * @param request 提交请求
     * @return 提交后主档详情
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecordDetailDto submit(SubmitHomepageRequest request) {
        // Gate：病案主键必填
        if (request.getCaseRecordId() == null) {
            throw new ValidationException("病案主档主键不能为空");
        }
        // Pre：组装提交命令
        SubmitCaseRecordCommand command = new SubmitCaseRecordCommand();
        command.setCaseRecordId(request.getCaseRecordId());
        command.setOperatorId(operatorResolver.requireOperatorId());
        // Cmd：领域校验首页完整性并迁移状态
        CaseRecord record = caseRecordDomainService.submit(command);
        // Asm：映射主档详情
        return caseRecordDetailMapper.toDetail(record);
    }

    /**
     * 病案室接收已提交病案（submitted → received）。
     *
     * @param request 接收请求
     * @return 接收后主档详情
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecordDetailDto receive(ReceiveHomepageRequest request) {
        // Gate：病案主键必填
        if (request.getCaseRecordId() == null) {
            throw new ValidationException("病案主档主键不能为空");
        }
        // Pre：组装病案室接收迁移命令
        TransitionCaseRecordCommand command = new TransitionCaseRecordCommand();
        command.setCaseRecordId(request.getCaseRecordId());
        command.setAction(CaseRecordTransitionAction.RECEIVE);
        command.setRole(WorkflowRole.RECORD_ROOM);
        command.setOperatorId(operatorResolver.requireOperatorId());
        // Cmd：领域状态机迁移
        CaseRecord record = caseRecordDomainService.transition(command);
        // Asm：映射主档详情
        return caseRecordDetailMapper.toDetail(record);
    }

    /**
     * 病案室退回医生修改（submitted/received → draft）。
     *
     * @param request 含退回原因
     * @return 退回后主档详情
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecordDetailDto returnToDoctor(ReturnToDoctorRequest request) {
        // Gate：病案主键与退回原因必填
        if (request.getCaseRecordId() == null) {
            throw new ValidationException("病案主档主键不能为空");
        }
        if (trimToNull(request.getReturnReason()) == null) {
            throw new ValidationException("退回原因不能为空");
        }
        // Pre：组装退回迁移命令
        TransitionCaseRecordCommand command = new TransitionCaseRecordCommand();
        command.setCaseRecordId(request.getCaseRecordId());
        command.setAction(CaseRecordTransitionAction.RETURN);
        command.setRole(WorkflowRole.RECORD_ROOM);
        command.setReturnReason(request.getReturnReason());
        command.setOperatorId(operatorResolver.requireOperatorId());
        // Cmd：领域状态机迁移并记录退回原因
        CaseRecord record = caseRecordDomainService.transition(command);
        // Asm：映射主档详情
        return caseRecordDetailMapper.toDetail(record);
    }

    /**
     * 生成病案首页 PDF 预览（OpenPDF 简化版式）。
     *
     * @param request 含病案主键
     * @return PDF Base64 与页数
     */
    public PdfGenerateResultDto generateHomepagePdf(GenerateHomepagePdfRequest request) {
        // Gate：病案主键必填
        if (request.getCaseRecordId() == null) {
            throw new ValidationException("病案主档主键不能为空");
        }
        // Pre：加载主档与首页子表
        CaseRecord record = caseRecordDomainService.requireById(request.getCaseRecordId());
        CaseRecordHomepage homepage = caseRecordHomepageDomainService.ensureHomepage(
                record.getId(), record.getTenantId(), operatorResolver.resolveOperatorIdOrNull());
        HomepageDto dto = homepageMapper.toDto(
                homepage,
                caseRecordHomepageDomainService.listDiagnoses(record.getId()),
                caseRecordHomepageDomainService.listProcedures(record.getId()));
        // Asm：渲染 PDF
        return homepageOpenPdfRenderer.render(dto);
    }
}
