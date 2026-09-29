/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.procedurecatalog.service;

import com.openhis.health.application.basic.procedurecatalog.dto.ClinicalProcV3ImportProgressResponse;
import com.openhis.health.application.basic.procedurecatalog.dto.GeneralOpImportProgressResponse;
import com.openhis.health.application.basic.procedurecatalog.dto.ProcedureCatalogDetailDto;
import com.openhis.health.application.basic.procedurecatalog.dto.ProcedureCatalogDto;
import com.openhis.health.application.basic.procedurecatalog.dto.ProcedureCatalogInitDto;
import com.openhis.health.application.basic.procedurecatalog.dto.StartClinicalProcV3ImportResponse;
import com.openhis.health.application.basic.procedurecatalog.dto.StartGeneralOpImportResponse;
import com.openhis.health.application.basic.procedurecatalog.mapper.ProcedureCatalogMapper;
import com.openhis.health.application.basic.procedurecatalog.request.CreateProcedureCatalogRequest;
import com.openhis.health.application.basic.procedurecatalog.request.GetClinicalProcV3ImportProgressRequest;
import com.openhis.health.application.basic.procedurecatalog.request.GetGeneralOpImportProgressRequest;
import com.openhis.health.application.basic.procedurecatalog.request.ImportClinicalProcV3CatalogRequest;
import com.openhis.health.application.basic.procedurecatalog.request.QueryProcedureCatalogRequest;
import com.openhis.health.application.basic.procedurecatalog.request.UpdateProcedureCatalogRequest;
import com.openhis.health.application.basic.procedurecatalog.support.ClinicalProcV3CatalogImportAsyncWorkflow;
import com.openhis.health.application.basic.procedurecatalog.support.ClinicalProcV3CatalogImportJob;
import com.openhis.health.application.basic.procedurecatalog.support.GeneralOpCatalogImportAsyncWorkflow;
import com.openhis.health.application.basic.procedurecatalog.support.GeneralOpCatalogImportJob;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.domain.clinical.proceduredefinition.command.EditProcedureDefinitionStartCommand;
import com.openhis.health.domain.clinical.proceduredefinition.command.EditProcedureDefinitionStopCommand;
import com.openhis.health.domain.clinical.proceduredefinition.queries.ProcedureDefinitionPageQuery;
import com.openhis.health.domain.clinical.proceduredefinition.queries.model.ProcedureDefinitionPageItemQueryModel;
import com.openhis.health.domain.clinical.proceduredefinition.service.ProcedureDefinitionDomainService;
import com.openhis.health.domain.clinical.proceduredefinition.service.ProcedureDefinitionQueryService;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 基础数据「手术操作目录」应用服务：编排 IF-BAS-POC-01～12（AS-BAS-MST-021）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>{@link ProcedureDefinitionQueryService}：分页/详情读侧</li>
 *   <li>{@link ProcedureDefinitionDomainService}：增改删启停写侧</li>
 *   <li>{@link ProcedureCatalogMapper}：Request↔Command、QueryModel↔DTO</li>
 *   <li>{@link CodeSystemQueryService}：来源/发布状态下拉</li>
 *   <li>两路 ImportAsyncWorkflow：国临/医保异步导入与进度</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar → POST /api/app/procedureCatalog/{methodName}}；不另写 RestController。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcedureCatalogAppService {

    /** 手术目录来源术语码系名（与 Mapper 展示文案解析一致）。 */
    private static final String PROCEDURE_SOURCE_CODE_SYSTEM =
        TerminologyCodeSystemNameConstants.PROCEDURE_DEFINITION_SOURCE;

    /** 手术定义读侧：分页与详情。 */
    private final ProcedureDefinitionQueryService procedureDefinitionQueryService;
    /** 手术定义写侧：增改删启停。 */
    private final ProcedureDefinitionDomainService procedureDefinitionDomainService;
    /** Request/Command 与 QueryModel/DTO 装配。 */
    private final ProcedureCatalogMapper procedureCatalogMapper;
    /** 术语下拉：来源与发布状态。 */
    private final CodeSystemQueryService codeSystemQueryService;
    /** 国临 3.0 异步导入工作流。 */
    private final ClinicalProcV3CatalogImportAsyncWorkflow clinicalProcV3CatalogImportAsyncWorkflow;
    /** 医保手术与操作异步导入工作流。 */
    private final GeneralOpCatalogImportAsyncWorkflow generalOpCatalogImportAsyncWorkflow;

    /**
     * 维护页初始化：加载来源与发布状态下拉（IF-BAS-POC-01）。
     *
     * @return 初始化 DTO
     */
    public ProcedureCatalogInitDto init() {
        // Gate：只读页初始化，无写侧操作人
        ProcedureCatalogInitDto dto = new ProcedureCatalogInitDto();
        // Pre：加载分类与发布状态下拉
        dto.setProcedureCategoryList(codeSystemQueryService.getOptionListByCodeSystemName(PROCEDURE_SOURCE_CODE_SYSTEM));
        dto.setStatusFlagOptions(codeSystemQueryService.getOptionListByEnum(PublicationStatus.class));
        // Asm：映射维护页初始化 DTO
        return dto;
    }

    /**
     * 分页查询手术目录（IF-BAS-POC-02；含挂靠 AD 回显）。
     *
     * @param query 分页请求；{@code null} 时使用第 1 页、每页 50 条
     * @return 分页列表 DTO
     */
    public PagedResult<ProcedureCatalogDto> pageProcedureCatalog(QueryProcedureCatalogRequest query) {
        // Gate：请求体缺省时补默认分页窗口
        QueryProcedureCatalogRequest request = ensureQuery(query);
        // Pre：HTTP 分页字段并入领域 ProcedureDefinitionPageQuery
        ProcedureDefinitionPageQuery pageQuery = new ProcedureDefinitionPageQuery();
        PageQueryBinder.bind(request, pageQuery);
        pageQuery.setSourceEnum(request.getSourceEnum());

        int pageSize;
        // 页大小非法时回落默认 50
        if (request.getPageSize() != null && request.getPageSize() > 0) {
            pageSize = request.getPageSize();
        } else {
            pageSize = 50;
        }

        // Pre：领域分页查询；按手术编码倒序
        Sort sort = Sort.by(Sort.Direction.DESC, FieldNameConstants.PROCEDURE_CODE);
        Page<ProcedureDefinitionPageItemQueryModel> page = procedureDefinitionQueryService.page(
            pageQuery,
            PageRequest.of(request.springPageIndex(), pageSize, sort));
        // Asm：映射分页 DTO（含 activityDefinitionId）
        return PagedResult.fromPage(page.map(procedureCatalogMapper::toDto));
    }

    /**
     * 新增手术目录并回读详情（IF-BAS-POC-04）。
     *
     * @param request 创建请求（含可选挂靠 AD）
     * @return 新建项详情 DTO
     */
    public ProcedureCatalogDto createProcedureCatalog(CreateProcedureCatalogRequest request) {
        // Gate：创建入参校验（名称/来源必填）
        validateCreateRequest(request);
        // Gate：发布状态空则默认 ACTIVE
        if (request.getStatusEnum() == null || request.getStatusEnum().isBlank()) {
            request.setStatusEnum(PublicationStatus.ACTIVE.getCode());
        }
        // Cmd：领域新增（含双码规范化、对端同步、可选挂靠 AD）
        Long id = procedureDefinitionDomainService.add(procedureCatalogMapper.toCreateCommand(request));
        // Pre：回读详情查询模型
        var detail = procedureDefinitionQueryService.getOne(id);
        // Asm：映射详情 DTO
        return procedureCatalogMapper.toDetailDto(detail);
    }

    /**
     * 编辑手术目录并回读详情（IF-BAS-POC-05）。
     *
     * @param request 更新请求（挂靠 AD 为 null 时不改存量）
     * @return 更新后详情 DTO
     */
    public ProcedureCatalogDto editProcedureCatalog(UpdateProcedureCatalogRequest request) {
        // Gate：更新入参校验
        validateUpdateRequest(request);
        // Cmd：领域编辑
        procedureDefinitionDomainService.edit(procedureCatalogMapper.toUpdateCommand(request));
        // Pre：回读详情查询模型
        var detail = procedureDefinitionQueryService.getOne(request.getId());
        // Asm：映射详情 DTO
        return procedureCatalogMapper.toDetailDto(detail);
    }

    /**
     * 按主键加载手术目录详情（IF-BAS-POC-03）。
     *
     * @param id 手术定义主键
     * @return 详情 DTO
     * @throws ValidationException id 为空
     */
    public ProcedureCatalogDetailDto getProcedureCatalog(Long id) {
        // Gate：主键非空
        if (id == null) {
            throw new ValidationException("查询手术目录失败：id 不能为空");
        }
        // Pre：读侧加载详情
        // Asm：映射详情 DTO
        return procedureCatalogMapper.toDetailDto(procedureDefinitionQueryService.getOne(id));
    }

    /**
     * 单条软删手术目录（IF-BAS-POC-06）。
     *
     * @param id 主键
     * @throws ValidationException id 为空
     */
    public void deleteProcedureCatalog(Long id) {
        // Gate：主键非空
        if (id == null) {
            throw new ValidationException("删除手术目录失败：id 不能为空");
        }
        // Cmd：领域软删
        procedureDefinitionDomainService.delete(id);
    }

    /**
     * 批量启用（IF-BAS-POC-07）。
     *
     * @param ids 主键列表，须非空
     * @throws ValidationException ids 为空
     */
    public void batchEnable(List<Long> ids) {
        // Gate：批量主键列表非空
        requireIds(ids, "启用手术目录失败：ids 不能为空");
        // Cmd：领域批量启用
        EditProcedureDefinitionStartCommand command = new EditProcedureDefinitionStartCommand();
        command.setIds(ids);
        procedureDefinitionDomainService.start(command);
    }

    /**
     * 批量停用（IF-BAS-POC-08）。
     *
     * @param ids 主键列表，须非空
     * @throws ValidationException ids 为空
     */
    public void batchDisable(List<Long> ids) {
        // Gate：批量主键列表非空
        requireIds(ids, "停用手术目录失败：ids 不能为空");
        // Cmd：领域批量停用
        EditProcedureDefinitionStopCommand command = new EditProcedureDefinitionStopCommand();
        command.setIds(ids);
        procedureDefinitionDomainService.stop(command);
    }

    /**
     * 启动国家临床版手术 3.0 异步导入（IF-BAS-POC-09）。
     *
     * @param request 含 fileBytes 的导入请求
     * @return jobId，供进度轮询
     * @throws ValidationException 文件空
     */
    public StartClinicalProcV3ImportResponse startImportClinicalProcV3Catalog(ImportClinicalProcV3CatalogRequest request) {
        // Gate：文件非空
        if (request == null || request.getFileBytes() == null || request.getFileBytes().length == 0) {
            throw new ValidationException("导入国家临床版手术3.0失败：文件不能为空");
        }
        // Cmd：登记异步导入任务（请求线程不写主表）
        String jobId = clinicalProcV3CatalogImportAsyncWorkflow.startImport(
            request.getFileBytes(), request.getFileName());
        // Asm：返回任务标识
        StartClinicalProcV3ImportResponse response = new StartClinicalProcV3ImportResponse();
        response.setJobId(jobId);
        return response;
    }

    /**
     * 查询国家临床版手术 3.0 异步导入进度（IF-BAS-POC-10）。
     *
     * @param request 含 jobId
     * @return 进度快照
     * @throws ValidationException jobId 空
     */
    public ClinicalProcV3ImportProgressResponse getImportClinicalProcV3CatalogProgress(
            GetClinicalProcV3ImportProgressRequest request) {
        // Gate：任务标识非空
        if (request == null || request.getJobId() == null || request.getJobId().isBlank()) {
            throw new ValidationException("查询导入进度失败：jobId 不能为空");
        }
        // Pre：读取工作流进度快照
        ClinicalProcV3CatalogImportJob job = clinicalProcV3CatalogImportAsyncWorkflow.getJob(request.getJobId());
        // Asm：装配进度响应
        ClinicalProcV3ImportProgressResponse response = new ClinicalProcV3ImportProgressResponse();
        response.setJobId(job.getJobId());
        response.setStatus(job.getStatus());
        response.setStage(job.getStage());
        response.setPercent(job.getPercent());
        response.setTotal(job.getTotal());
        response.setProcessed(job.getProcessed());
        response.setSuccessCount(job.getSuccessCount());
        response.setMessage(job.getMessage());
        return response;
    }

    /**
     * 启动医保「手术与操作」异步导入（IF-BAS-POC-11）。
     *
     * @return jobId，供进度轮询
     */
    public StartGeneralOpImportResponse startImportGeneralOpCatalog() {
        // Gate：无额外入参
        // Cmd：登记医保导入异步任务
        String jobId = generalOpCatalogImportAsyncWorkflow.startImport();
        // Asm：返回任务标识
        StartGeneralOpImportResponse response = new StartGeneralOpImportResponse();
        response.setJobId(jobId);
        return response;
    }

    /**
     * 查询医保「手术与操作」异步导入进度（IF-BAS-POC-12）。
     *
     * @param request 含 jobId
     * @return 进度快照
     * @throws ValidationException jobId 空
     */
    public GeneralOpImportProgressResponse getImportGeneralOpCatalogProgress(GetGeneralOpImportProgressRequest request) {
        // Gate：任务标识非空
        if (request == null || request.getJobId() == null || request.getJobId().isBlank()) {
            throw new ValidationException("查询导入进度失败：jobId 不能为空");
        }
        // Pre：读取工作流进度快照
        GeneralOpCatalogImportJob job = generalOpCatalogImportAsyncWorkflow.getJob(request.getJobId());
        // Asm：装配进度响应
        GeneralOpImportProgressResponse response = new GeneralOpImportProgressResponse();
        response.setJobId(job.getJobId());
        response.setStatus(job.getStatus());
        response.setStage(job.getStage());
        response.setPercent(job.getPercent());
        response.setTotal(job.getTotal());
        response.setProcessed(job.getProcessed());
        response.setSuccessCount(job.getSuccessCount());
        response.setMessage(job.getMessage());
        return response;
    }

    /**
     * 分页请求缺省时补 pageNo=1、pageSize=50。
     *
     * @param query 原始请求
     * @return 非空请求
     */
    private QueryProcedureCatalogRequest ensureQuery(QueryProcedureCatalogRequest query) {
        if (query != null) {
            return query;
        }
        QueryProcedureCatalogRequest request = new QueryProcedureCatalogRequest();
        request.setPageNo(1);
        request.setPageSize(50);
        return request;
    }

    /**
     * 新增写前校验：请求体、名称、来源非空。
     *
     * @param request 新增请求
     * @throws ValidationException 必填缺失
     */
    private void validateCreateRequest(CreateProcedureCatalogRequest request) {
        if (request == null) {
            throw new ValidationException("新增手术目录失败：请求体不能为空");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new ValidationException("新增手术目录失败：名称不能为空");
        }
        if (request.getSourceEnum() == null || request.getSourceEnum().isBlank()) {
            throw new ValidationException("新增手术目录失败：手术类型不能为空");
        }
    }

    /**
     * 编辑写前校验：主键、名称、来源非空。
     *
     * @param request 更新请求
     * @throws ValidationException 必填缺失
     */
    private void validateUpdateRequest(UpdateProcedureCatalogRequest request) {
        if (request == null || request.getId() == null) {
            throw new ValidationException("更新手术目录失败：id 不能为空");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new ValidationException("更新手术目录失败：名称不能为空");
        }
        if (request.getSourceEnum() == null || request.getSourceEnum().isBlank()) {
            throw new ValidationException("更新手术目录失败：手术类型不能为空");
        }
    }

    /**
     * 批量 ids 非空门禁。
     *
     * @param ids     主键列表
     * @param message 为空时提示
     * @throws ValidationException ids 空
     */
    private void requireIds(List<Long> ids, String message) {
        if (ids == null || ids.isEmpty()) {
            throw new ValidationException(message);
        }
    }
}
