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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 基础数据「手术操作目录」应用服务：对外维护国临/院内手术定义，对内编排
 * {@link ProcedureDefinitionQueryService} 与 {@link ProcedureDefinitionDomainService}。
 */
@Service
@RequiredArgsConstructor
public class ProcedureCatalogAppService {

    private static final String PROCEDURE_SOURCE_CODE_SYSTEM = TerminologyCodeSystemNameConstants.PROCEDURE_DEFINITION_SOURCE;

    private final ProcedureDefinitionQueryService procedureDefinitionQueryService;
    private final ProcedureDefinitionDomainService procedureDefinitionDomainService;
    private final ProcedureCatalogMapper procedureCatalogMapper;
    private final CodeSystemQueryService codeSystemQueryService;
    private final ClinicalProcV3CatalogImportAsyncWorkflow clinicalProcV3CatalogImportAsyncWorkflow;
    private final GeneralOpCatalogImportAsyncWorkflow generalOpCatalogImportAsyncWorkflow;

    /**
     * 返回手术目录维护页所需的下拉：分类、发布状态。
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
     * 分页查询手术目录：关键字与来源分类下推到领域分页查询。
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
        // Asm：映射分页 DTO
        return PagedResult.fromPage(page.map(procedureCatalogMapper::toDto));
    }

    /**
     * 新增手术目录项：校验后默认状态为「生效」，创建完成后返回详情形状。
     *
     * @param request 创建请求
     * @return 新建项详情 DTO
     */
    public ProcedureCatalogDto createProcedureCatalog(CreateProcedureCatalogRequest request) {
        // Gate：创建入参校验
        validateCreateRequest(request);
        if (request.getStatusEnum() == null || request.getStatusEnum().isBlank()) {
            request.setStatusEnum(PublicationStatus.ACTIVE.getCode());
        }
        // Cmd：领域新增手术目录
        Long id = procedureDefinitionDomainService.add(procedureCatalogMapper.toCreateCommand(request));
        // Pre：回读详情查询模型
        var detail = procedureDefinitionQueryService.getOne(id);
        // Asm：映射详情 DTO
        return procedureCatalogMapper.toDetailDto(detail);
    }

    /**
     * 编辑手术目录项并回读详情。
     *
     * @param request 更新请求
     * @return 更新后详情 DTO
     */
    public ProcedureCatalogDto editProcedureCatalog(UpdateProcedureCatalogRequest request) {
        // Gate：更新入参校验
        validateUpdateRequest(request);
        // Cmd：领域编辑手术目录
        procedureDefinitionDomainService.edit(procedureCatalogMapper.toUpdateCommand(request));
        // Pre：回读详情查询模型
        var detail = procedureDefinitionQueryService.getOne(request.getId());
        // Asm：映射详情 DTO
        return procedureCatalogMapper.toDetailDto(detail);
    }

    /**
     * 按主键加载手术目录详情。
     *
     * @param id 手术定义主键
     * @return 详情 DTO
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
     * 单条删除手术目录（软删，列表不再展示）。
     *
     * @param id 主键
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
     * 批量启用（发布）手术目录项。
     *
     * @param ids 主键列表，须非空
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
     * 批量停用手术目录项。
     *
     * @param ids 主键列表，须非空
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
     * 启动国家临床版手术 3.0 异步导入。
     *
     * @param request 含 fileBytes 的导入请求
     * @return jobId，供进度轮询
     */
    public StartClinicalProcV3ImportResponse startImportClinicalProcV3Catalog(ImportClinicalProcV3CatalogRequest request) {
        if (request == null || request.getFileBytes() == null || request.getFileBytes().length == 0) {
            throw new ValidationException("导入国家临床版手术3.0失败：文件不能为空");
        }
        String jobId = clinicalProcV3CatalogImportAsyncWorkflow.startImport(
            request.getFileBytes(), request.getFileName());
        StartClinicalProcV3ImportResponse response = new StartClinicalProcV3ImportResponse();
        response.setJobId(jobId);
        return response;
    }

    /**
     * 查询国家临床版手术 3.0 异步导入进度。
     *
     * @param request 含 jobId
     * @return 进度快照
     */
    public ClinicalProcV3ImportProgressResponse getImportClinicalProcV3CatalogProgress(
            GetClinicalProcV3ImportProgressRequest request) {
        if (request == null || request.getJobId() == null || request.getJobId().isBlank()) {
            throw new ValidationException("查询导入进度失败：jobId 不能为空");
        }
        ClinicalProcV3CatalogImportJob job = clinicalProcV3CatalogImportAsyncWorkflow.getJob(request.getJobId());
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
     * 启动医保「手术与操作」异步导入（从 yb_national_catalog_item list_type=1308）。
     *
     * @return jobId，供进度轮询
     */
    public StartGeneralOpImportResponse startImportGeneralOpCatalog() {
        String jobId = generalOpCatalogImportAsyncWorkflow.startImport();
        StartGeneralOpImportResponse response = new StartGeneralOpImportResponse();
        response.setJobId(jobId);
        return response;
    }

    /**
     * 查询医保「手术与操作」异步导入进度。
     *
     * @param request 含 jobId
     * @return 进度快照
     */
    public GeneralOpImportProgressResponse getImportGeneralOpCatalogProgress(GetGeneralOpImportProgressRequest request) {
        if (request == null || request.getJobId() == null || request.getJobId().isBlank()) {
            throw new ValidationException("查询导入进度失败：jobId 不能为空");
        }
        GeneralOpCatalogImportJob job = generalOpCatalogImportAsyncWorkflow.getJob(request.getJobId());
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

    private QueryProcedureCatalogRequest ensureQuery(QueryProcedureCatalogRequest query) {
        if (query != null) {
            return query;
        }
        QueryProcedureCatalogRequest request = new QueryProcedureCatalogRequest();
        request.setPageNo(1);
        request.setPageSize(50);
        return request;
    }

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

    private void requireIds(List<Long> ids, String message) {
        if (ids == null || ids.isEmpty()) {
            throw new ValidationException(message);
        }
    }
}
