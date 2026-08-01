/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.diagnosiscatalog.service;

import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.application.basic.diagnosiscatalog.dto.ClinicalV2ImportProgressResponse;
import com.openhis.health.application.basic.diagnosiscatalog.dto.DiagnosisCatalogDetailDto;
import com.openhis.health.application.basic.diagnosiscatalog.dto.DiagnosisCatalogDto;
import com.openhis.health.application.basic.diagnosiscatalog.dto.DiagnosisCatalogInitDto;
import com.openhis.health.application.basic.diagnosiscatalog.dto.ImportClinicalV2CatalogResponse;
import com.openhis.health.application.basic.diagnosiscatalog.dto.StartClinicalV2ImportResponse;
import com.openhis.health.application.basic.diagnosiscatalog.dto.StartYbConditionImportResponse;
import com.openhis.health.application.basic.diagnosiscatalog.dto.YbConditionImportProgressResponse;
import com.openhis.health.application.basic.diagnosiscatalog.mapper.DiagnosisCatalogMapper;
import com.openhis.health.application.basic.diagnosiscatalog.request.CreateDiagnosisCatalogRequest;
import com.openhis.health.application.basic.diagnosiscatalog.request.GetClinicalV2ImportProgressRequest;
import com.openhis.health.application.basic.diagnosiscatalog.request.GetYbConditionImportProgressRequest;
import com.openhis.health.application.basic.diagnosiscatalog.request.ImportClinicalV2CatalogRequest;
import com.openhis.health.application.basic.diagnosiscatalog.request.QueryDiagnosisCatalogRequest;
import com.openhis.health.application.basic.diagnosiscatalog.request.StartYbConditionImportRequest;
import com.openhis.health.application.basic.diagnosiscatalog.request.UpdateDiagnosisCatalogRequest;
import com.openhis.health.application.basic.diagnosiscatalog.support.ClinicalV2CatalogImportAsyncWorkflow;
import com.openhis.health.application.basic.diagnosiscatalog.support.ClinicalV2CatalogImportJob;
import com.openhis.health.application.basic.diagnosiscatalog.support.ClinicalV2CatalogWorkbookParser;
import com.openhis.health.application.basic.diagnosiscatalog.support.YbConditionCatalogImportAsyncWorkflow;
import com.openhis.health.application.basic.diagnosiscatalog.support.YbConditionCatalogImportJob;
import com.openhis.health.domain.clinical.conditiondefinition.command.ClinicalV2ImportRowCommand;
import com.openhis.health.domain.clinical.conditiondefinition.command.EditDiseaseStartCommand;
import com.openhis.health.domain.clinical.conditiondefinition.command.EditDiseaseStopCommand;
import com.openhis.health.domain.clinical.conditiondefinition.queries.DiseasePageQuery;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.DiseasePageItemQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionDomainService;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.share.constants.FieldNameConstants;
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
 * 基础数据「疾病（病种）目录」应用服务：对外维护 ICD/院内病种等条件定义，对内编排
 * {@link ConditionDefinitionQueryService} 与 {@link ConditionDefinitionDomainService}。
 *
 * <p>初始化下拉的码表名取自 {@link TerminologyCodeSystemNameConstants}，与 Mapper 内解析展示文案的术语集保持一致。</p>
 */
@Service
@RequiredArgsConstructor
public class DiagnosisCatalogAppService {

    /** 疾病来源/分类术语集名称（与 Mapper、初始化接口共用）。 */
    private static final String CONDITION_SOURCE_CODE_SYSTEM = TerminologyCodeSystemNameConstants.CONDITION_DEFINITION_SOURCE;
    /** 医保诊断类型术语集名称。 */
    private static final String DIAG_TYPE_CODE_SYSTEM = TerminologyCodeSystemNameConstants.DIAG_TYPE;

    /** 条件定义读侧：分页列表与单条详情。 */
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;
    /** 条件定义写侧：新增、编辑与批量启停。 */
    private final ConditionDefinitionDomainService conditionDefinitionDomainService;
    /** 疾病目录 DTO 与领域命令装配。 */
    private final DiagnosisCatalogMapper diagnosisMapper;
    /** 术语下拉：分类、医保诊断类型等初始化选项。 */
    private final CodeSystemQueryService codeSystemQueryService;
    /** 国家临床版 2.0 Excel 解析。 */
    private final ClinicalV2CatalogWorkbookParser clinicalV2CatalogWorkbookParser;
    /** 国家临床版 2.0 异步导入。 */
    private final ClinicalV2CatalogImportAsyncWorkflow clinicalV2CatalogImportAsyncWorkflow;
    /** 医保诊断目录异步导入。 */
    private final YbConditionCatalogImportAsyncWorkflow ybConditionCatalogImportAsyncWorkflow;

    /**
     * 返回疾病目录维护页所需的下拉：分类、发布状态、医保诊断类型。
     *
     * @return 初始化 DTO，三项列表均由术语或枚举选项填充
     */
    public DiagnosisCatalogInitDto init() {
        // Gate：只读页初始化，无写侧操作人
        DiagnosisCatalogInitDto dto = new DiagnosisCatalogInitDto();
        // Pre：加载分类、发布状态与医保诊断类型下拉
        dto.setDiseaseCategoryList(codeSystemQueryService.getOptionListByCodeSystemName(CONDITION_SOURCE_CODE_SYSTEM));
        dto.setStatusFlagOptions(codeSystemQueryService.getOptionListByEnum(PublicationStatus.class));
        dto.setYbDiagTypes(codeSystemQueryService.getOptionListByCodeSystemName(DIAG_TYPE_CODE_SYSTEM));
        // Asm：映射维护页初始化 DTO
        return dto;
    }

    /**
     * 分页查询疾病目录：关键字与来源分类下推到领域分页查询。
     *
     * @param query 分页请求；{@code null} 时使用第 1 页、每页 50 条
     * @return 分页列表 DTO
     */
    public PagedResult<DiagnosisCatalogDto> pageDiagnosisCatalog(QueryDiagnosisCatalogRequest query) {
        // Gate：请求体缺省时补默认分页窗口
        QueryDiagnosisCatalogRequest request = ensureQuery(query);
        // Pre：HTTP 分页字段并入领域 DiseasePageQuery
        DiseasePageQuery pageQuery = new DiseasePageQuery();
        PageQueryBinder.bind(request, pageQuery);
        pageQuery.setSourceEnum(request.getSourceEnum());

        int pageSize;
        // 业务分支：页大小非法时回落默认 50
        if (request.getPageSize() != null && request.getPageSize() > 0) {
            pageSize = request.getPageSize();
        } else {
            pageSize = 50;
        }

        // Pre：领域分页查询（Spring Data 页下标 0-based）；按疾病编码倒序，避免无序/按更新时间导致编辑后行位跳动
        Sort sort = Sort.by(Sort.Direction.DESC, FieldNameConstants.CONDITION_CODE);
        Page<DiseasePageItemQueryModel> page = conditionDefinitionQueryService.getDiseaseList(
            pageQuery,
            PageRequest.of(request.springPageIndex(), pageSize, sort));
        // Asm：映射分页 DTO
        return PagedResult.fromPage(page.map(diagnosisMapper::toDto));
    }

    /**
     * 新增疾病目录项：校验后默认状态为「生效」，创建完成后返回详情形状（与编辑接口一致）。
     *
     * @param request 创建请求
     * @return 新建项详情 DTO（与列表项字段口径不同，含更多编辑字段）
     * @throws ValidationException 参数不合法时
     */
    public DiagnosisCatalogDto createDiagnosisCatalog(CreateDiagnosisCatalogRequest request) {
        // Gate：创建入参校验（名称与来源必填）
        validateCreateRequest(request);
        // 业务分支：未传状态时默认 ACTIVE，与列表筛选一致
        if (request.getStatusEnum() == null || request.getStatusEnum().isBlank()) {
            request.setStatusEnum(PublicationStatus.ACTIVE.getCode());
        }
        // Cmd：领域新增疾病目录
        Long id = conditionDefinitionDomainService.addDisease(diagnosisMapper.toCreateCommand(request));
        // Pre：回读详情查询模型
        var detail = conditionDefinitionQueryService.getDiseaseOne(id);
        // Asm：映射详情 DTO 供编辑页复用
        return diagnosisMapper.toDetailDto(detail);
    }

    /**
     * 编辑疾病目录项并回读详情。
     *
     * @param request 更新请求
     * @return 更新后详情 DTO
     * @throws ValidationException 参数不合法时
     */
    public DiagnosisCatalogDto editDiagnosisCatalog(UpdateDiagnosisCatalogRequest request) {
        // Gate：更新入参校验
        validateUpdateRequest(request);
        // Cmd：领域编辑疾病目录
        conditionDefinitionDomainService.editDisease(diagnosisMapper.toUpdateCommand(request));
        // Pre：回读详情查询模型
        var detail = conditionDefinitionQueryService.getDiseaseOne(request.getId());
        // Asm：映射详情 DTO
        return diagnosisMapper.toDetailDto(detail);
    }

    /**
     * 按主键加载疾病目录详情。
     *
     * @param id 条件定义主键
     * @return 详情 DTO
     * @throws ValidationException id 为空时
     */
    public DiagnosisCatalogDetailDto getDiagnosisCatalog(Long id) {
        // Gate：主键非空
        if (id == null) {
            throw new ValidationException("查询疾病目录失败：id 不能为空");
        }
        // Pre：读侧加载详情
        // Asm：映射详情 DTO
        return diagnosisMapper.toDetailDto(conditionDefinitionQueryService.getDiseaseOne(id));
    }

    /**
     * 单条删除疾病目录（软删，列表不再展示）。
     *
     * @param id 主键
     * @throws ValidationException id 为空时
     */
    public void deleteDiagnosisCatalog(Long id) {
        // Gate：主键非空
        if (id == null) {
            throw new ValidationException("删除疾病目录失败：id 不能为空");
        }
        // Cmd：领域软删
        conditionDefinitionDomainService.delete(id);
    }

    /**
     * 批量启用（发布）疾病目录项。
     *
     * @param ids 主键列表，须非空
     * @throws ValidationException ids 为空时
     */
    public void batchEnable(List<Long> ids) {
        // Gate：批量主键列表非空
        requireIds(ids, "启用疾病目录失败：ids 不能为空");
        // Cmd：领域批量启用
        EditDiseaseStartCommand command = new EditDiseaseStartCommand();
        command.setIds(ids);
        conditionDefinitionDomainService.editDiseaseStart(command);
    }

    /**
     * 批量停用疾病目录项。
     *
     * @param ids 主键列表，须非空
     * @throws ValidationException ids 为空时
     */
    public void batchDisable(List<Long> ids) {
        // Gate：批量主键列表非空
        requireIds(ids, "停用疾病目录失败：ids 不能为空");
        // Cmd：领域批量停用
        EditDiseaseStopCommand command = new EditDiseaseStopCommand();
        command.setIds(ids);
        conditionDefinitionDomainService.editDiseaseStop(command);
    }

    /**
     * 启动国家临床版 2.0 异步导入（推荐；避免全量 3 万+ 行 HTTP 超时）。
     *
     * @param request 含 fileBytes 的导入请求
     * @return jobId，供进度轮询
     */
    public StartClinicalV2ImportResponse startImportClinicalV2Catalog(ImportClinicalV2CatalogRequest request) {
        if (request == null || request.getFileBytes() == null || request.getFileBytes().length == 0) {
            throw new ValidationException("导入国家临床版2.0失败：文件不能为空");
        }
        String jobId = clinicalV2CatalogImportAsyncWorkflow.startImport(
            request.getFileBytes(), request.getFileName());
        StartClinicalV2ImportResponse response = new StartClinicalV2ImportResponse();
        response.setJobId(jobId);
        return response;
    }

    /**
     * 查询国家临床版 2.0 异步导入进度。
     *
     * @param request 含 jobId
     * @return 进度快照
     */
    public ClinicalV2ImportProgressResponse getImportClinicalV2CatalogProgress(
            GetClinicalV2ImportProgressRequest request) {
        if (request == null || request.getJobId() == null || request.getJobId().isBlank()) {
            throw new ValidationException("查询导入进度失败：jobId 不能为空");
        }
        ClinicalV2CatalogImportJob job = clinicalV2CatalogImportAsyncWorkflow.getJob(request.getJobId());
        ClinicalV2ImportProgressResponse response = new ClinicalV2ImportProgressResponse();
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
     * 启动医保诊断目录异步导入（从 yb_national_catalog_item 按 list_type 灌入）。
     *
     * @param request 含 sourceEnum
     * @return jobId，供进度轮询
     */
    public StartYbConditionImportResponse startImportYbConditionCatalog(StartYbConditionImportRequest request) {
        if (request == null || request.getSourceEnum() == null || request.getSourceEnum().isBlank()) {
            throw new ValidationException("从医保导入失败：sourceEnum 不能为空");
        }
        String jobId = ybConditionCatalogImportAsyncWorkflow.startImport(request.getSourceEnum());
        StartYbConditionImportResponse response = new StartYbConditionImportResponse();
        response.setJobId(jobId);
        return response;
    }

    /**
     * 查询医保诊断目录异步导入进度。
     *
     * @param request 含 jobId
     * @return 进度快照
     */
    public YbConditionImportProgressResponse getImportYbConditionCatalogProgress(
            GetYbConditionImportProgressRequest request) {
        if (request == null || request.getJobId() == null || request.getJobId().isBlank()) {
            throw new ValidationException("查询导入进度失败：jobId 不能为空");
        }
        YbConditionCatalogImportJob job = ybConditionCatalogImportAsyncWorkflow.getJob(request.getJobId());
        YbConditionImportProgressResponse response = new YbConditionImportProgressResponse();
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
     * 同步导入国家临床版 2.0（兼容旧调用；全量文件易超时，页面请用异步接口）。
     *
     * @param request 含 fileBytes 的导入请求
     * @return 解析行数与写入条数
     * @throws ValidationException 文件为空或无法解析时
     */
    public ImportClinicalV2CatalogResponse importClinicalV2Catalog(ImportClinicalV2CatalogRequest request) {
        // Gate：上传字节非空
        if (request == null || request.getFileBytes() == null || request.getFileBytes().length == 0) {
            throw new ValidationException("导入国家临床版2.0失败：文件不能为空");
        }
        // Pre：解析 Excel
        List<ClinicalV2ImportRowCommand> rows = clinicalV2CatalogWorkbookParser.parse(request.getFileBytes());
        // Cmd：领域批量 upsert
        int success = conditionDefinitionDomainService.importClinicalV2Catalog(rows);
        // Asm：导入结果
        ImportClinicalV2CatalogResponse response = new ImportClinicalV2CatalogResponse();
        response.setTotalCount(rows.size());
        response.setSuccessCount(success);
        return response;
    }

    /**
     * 为 null 查询请求补全默认分页参数。
     *
     * @param query 原始请求
     * @return 非 null 请求
     */
    private QueryDiagnosisCatalogRequest ensureQuery(QueryDiagnosisCatalogRequest query) {
        if (query != null) {
            return query;
        }
        QueryDiagnosisCatalogRequest request = new QueryDiagnosisCatalogRequest();
        request.setPageNo(1);
        request.setPageSize(50);
        return request;
    }

    /**
     * 新增入参校验：名称与疾病类型（来源）必填。
     *
     * @param request 创建请求
     * @throws ValidationException 不满足时
     */
    private void validateCreateRequest(CreateDiagnosisCatalogRequest request) {
        if (request == null) {
            throw new ValidationException("新增疾病目录失败：请求体不能为空");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new ValidationException("新增疾病目录失败：名称不能为空");
        }
        if (request.getSourceEnum() == null || request.getSourceEnum().isBlank()) {
            throw new ValidationException("新增疾病目录失败：疾病类型不能为空");
        }
    }

    /**
     * 更新入参校验：主键、名称与疾病类型必填。
     *
     * @param request 更新请求
     * @throws ValidationException 不满足时
     */
    private void validateUpdateRequest(UpdateDiagnosisCatalogRequest request) {
        if (request == null || request.getId() == null) {
            throw new ValidationException("更新疾病目录失败：id 不能为空");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new ValidationException("更新疾病目录失败：名称不能为空");
        }
        if (request.getSourceEnum() == null || request.getSourceEnum().isBlank()) {
            throw new ValidationException("更新疾病目录失败：疾病类型不能为空");
        }
    }

    /**
     * 批量操作前校验主键列表非空。
     *
     * @param ids    主键集合
     * @param message 为空时的完整提示语
     * @throws ValidationException ids 为 null 或 empty 时
     */
    private void requireIds(List<Long> ids, String message) {
        if (ids == null || ids.isEmpty()) {
            throw new ValidationException(message);
        }
    }
}
