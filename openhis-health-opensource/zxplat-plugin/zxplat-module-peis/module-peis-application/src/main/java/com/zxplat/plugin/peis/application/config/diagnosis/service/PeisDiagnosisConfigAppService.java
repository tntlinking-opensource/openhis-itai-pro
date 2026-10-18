/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.config.diagnosis.service;

import com.zxplat.plugin.peis.application.config.diagnosis.dto.PeisCommonResultDto;
import com.zxplat.plugin.peis.application.config.diagnosis.dto.PeisDiagnosisTemplateDto;
import com.zxplat.plugin.peis.application.config.diagnosis.mapper.PeisDiagnosisConfigMapper;
import com.zxplat.plugin.peis.application.config.diagnosis.request.SaveCommonResultRequest;
import com.zxplat.plugin.peis.application.config.diagnosis.request.SaveDiagnosisTemplateRequest;
import com.zxplat.plugin.peis.application.config.diagnosis.request.SearchCommonResultRequest;
import com.zxplat.plugin.peis.application.config.diagnosis.request.SearchDiagnosisTemplateRequest;
import com.zxplat.plugin.peis.application.config.diagnosis.response.SearchCommonResultResponse;
import com.zxplat.plugin.peis.application.config.diagnosis.response.SearchDiagnosisTemplateResponse;
import com.zxplat.framework.core.application.paging.PageQueryBinder;
import com.zxplat.framework.core.application.paging.PageWindowGate;
import com.zxplat.framework.core.application.paging.PagedResultBuilder;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.clinical.conditiondefinition.queries.DiseasePageQuery;
import com.zxplat.domain.clinical.conditiondefinition.queries.model.DiseaseDetailQueryModel;
import com.zxplat.domain.clinical.conditiondefinition.queries.model.DiseasePageItemQueryModel;
import com.zxplat.domain.clinical.conditiondefinition.service.ConditionDefinitionDomainService;
import com.zxplat.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.zxplat.plugin.peis.domain.commonresult.entity.PeisCommonResult;
import com.zxplat.plugin.peis.domain.commonresult.queries.PeisCommonResultQuery;
import com.zxplat.plugin.peis.domain.commonresult.queries.model.PeisCommonResultModel;
import com.zxplat.plugin.peis.domain.commonresult.service.PeisCommonResultDomainService;
import com.zxplat.plugin.peis.domain.commonresult.service.PeisCommonResultQueryService;
import com.zxplat.domain.share.constants.PeisCatalogConstants;
import com.zxplat.domain.share.constants.FieldNameConstants;
import com.zxplat.framework.core.application.exception.ValidationException;
import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 诊断与表达式配置应用服务（AS-PEIS-CFG-002）。
 *
 * <p>P0：诊断建议库（CFG-08）与常见结果（CFG-09）；不含 CFG-10/12 自动表达式。</p>
 */
@Service
@RequiredArgsConstructor
public class PeisDiagnosisConfigAppService {

    /** 体检诊断建议库专用来源；不与院内诊断 / 医生站候选混用。 */
    private static final String DEFAULT_DIAGNOSIS_SOURCE = PeisCatalogConstants.CONDITION_SOURCE_PEIS_DX;

    private final ConditionDefinitionQueryService conditionDefinitionQueryService;
    private final ConditionDefinitionDomainService conditionDefinitionDomainService;
    private final PeisCommonResultQueryService peisCommonResultQueryService;
    private final PeisCommonResultDomainService peisCommonResultDomainService;
    private final PeisDiagnosisConfigMapper peisDiagnosisConfigMapper;
    /** 分页窗口入站门控（B.10.11）。 */
    private final PageWindowGate pageWindowGate;

    /**
     * 分页检索诊断建议库（CFG-08）。
     */
    public SearchDiagnosisTemplateResponse searchDiagnosisTemplate(SearchDiagnosisTemplateRequest request) {
        // Gate：请求体缺省时补默认分页，再校验窗口
        SearchDiagnosisTemplateRequest query = ensureDiagnosisRequest(request);
        pageWindowGate.requirePositivePageWindow(
            query, ErrorCodeConstants.PEIS_CONFIG_INVALID_ARGUMENT, "诊断建议库分页参数非法");
        // Pre：HTTP 分页并入领域 DiseasePageQuery
        DiseasePageQuery pageQuery = new DiseasePageQuery();
        PageQueryBinder.bind(query, pageQuery);
        pageQuery.setSourceEnum(peisDiagnosisSource());
        if (StringUtils.isNotEmpty(query.getStatusEnum())) {
            pageQuery.setStatusEnum(query.getStatusEnum().trim());
        }
        Sort sort = Sort.by(Sort.Direction.DESC, FieldNameConstants.CONDITION_CODE);
        Page<DiseasePageItemQueryModel> page = conditionDefinitionQueryService.getDiseaseList(
            pageQuery,
            PageRequest.of(query.springPageIndex(), query.getBoundedPageSize(), sort));
        List<PeisDiagnosisTemplateDto> content = new ArrayList<>();
        for (DiseasePageItemQueryModel row : page.getContent()) {
            content.add(peisDiagnosisConfigMapper.toDiagnosisTemplateDto(row));
        }
        // Asm：装配分页响应
        return PagedResultBuilder.build(new SearchDiagnosisTemplateResponse(), content, page);
    }

    /**
     * 保存诊断建议库模板（CFG-08）。
     */
    public PeisDiagnosisTemplateDto saveDiagnosisTemplate(SaveDiagnosisTemplateRequest request) {
        // Gate：必填字段校验
        validateSaveDiagnosisTemplateRequest(request);
        String sourceEnum = peisDiagnosisSource();
        Long definitionId;
        if (request.getDefinitionId() == null) {
            // Cmd：领域新增诊断目录
            definitionId = conditionDefinitionDomainService.addDisease(
                peisDiagnosisConfigMapper.toAddDiseaseCommand(request, sourceEnum));
        } else {
            definitionId = request.getDefinitionId();
            // Cmd：领域编辑诊断目录
            conditionDefinitionDomainService.editDisease(
                peisDiagnosisConfigMapper.toEditDiseaseCommand(request, sourceEnum));
        }
        // Pre：回读详情
        DiseaseDetailQueryModel detail = conditionDefinitionQueryService.getDiseaseOne(definitionId);
        // Asm：映射 DTO
        return peisDiagnosisConfigMapper.toDiagnosisTemplateDto(detail);
    }

    /**
     * 分页检索常见结果（CFG-09）。
     */
    public SearchCommonResultResponse searchCommonResult(SearchCommonResultRequest request) {
        // Gate：请求体缺省时补默认分页，再校验窗口
        SearchCommonResultRequest query = ensureCommonResultRequest(request);
        pageWindowGate.requirePositivePageWindow(
            query, ErrorCodeConstants.PEIS_COMMON_RESULT_INVALID_ARGUMENT, "常见结果分页参数非法");
        // Pre：组装常见结果查询
        PeisCommonResultQuery pageQuery = new PeisCommonResultQuery();
        pageQuery.setTenantId(TenantUtils.getCurrentTenantId());
        pageQuery.setObservationDefinitionId(query.getObservationDefinitionId());
        pageQuery.setDeptOrgId(query.getDeptOrgId());
        if (StringUtils.isNotEmpty(query.getKeyword())) {
            pageQuery.setKeyword(query.getKeyword().trim());
        }
        if (StringUtils.isNotEmpty(query.getStatusEnum())) {
            pageQuery.setStatusEnum(query.getStatusEnum().trim());
        }
        Sort sort = Sort.by(Sort.Direction.ASC, FieldNameConstants.DISPLAY);
        Page<PeisCommonResultModel> page = peisCommonResultQueryService.search(
            pageQuery,
            PageRequest.of(query.springPageIndex(), query.getBoundedPageSize(), sort));
        List<PeisCommonResultDto> content = new ArrayList<>();
        for (PeisCommonResultModel row : page.getContent()) {
            content.add(peisDiagnosisConfigMapper.toCommonResultDto(row));
        }
        // Asm：装配分页响应
        return PagedResultBuilder.build(new SearchCommonResultResponse(), content, page);
    }

    /**
     * 保存常见结果（CFG-09）。
     */
    public PeisCommonResultDto saveCommonResult(SaveCommonResultRequest request) {
        // Gate：必填字段校验
        validateSaveCommonResultRequest(request);
        // Cmd：领域保存常见结果
        PeisCommonResult saved = peisCommonResultDomainService.save(
            peisDiagnosisConfigMapper.toSaveCommonResultCommand(request, TenantUtils.getCurrentTenantId()));
        // Asm：映射 DTO
        return peisDiagnosisConfigMapper.toCommonResultDto(saved);
    }

    /**
     * 缺省请求或未传分页键时补 pageNo=1 / pageSize=50。
     *
     * @param request 入站请求，可为 null
     * @return 非 null 且带合法分页窗口的请求
     */
    private SearchDiagnosisTemplateRequest ensureDiagnosisRequest(SearchDiagnosisTemplateRequest request) {
        SearchDiagnosisTemplateRequest query = request;
        if (query == null) {
            query = new SearchDiagnosisTemplateRequest();
        }
        if (query.getPageNo() == null || query.getPageNo() < 1) {
            query.setPageNo(1);
        }
        if (query.getPageSize() == null || query.getPageSize() < 1) {
            query.setPageSize(50);
        }
        return query;
    }

    /**
     * 缺省请求或未传分页键时补 pageNo=1 / pageSize=50。
     *
     * @param request 入站请求，可为 null
     * @return 非 null 且带合法分页窗口的请求
     */
    private SearchCommonResultRequest ensureCommonResultRequest(SearchCommonResultRequest request) {
        SearchCommonResultRequest query = request;
        if (query == null) {
            query = new SearchCommonResultRequest();
        }
        if (query.getPageNo() == null || query.getPageNo() < 1) {
            query.setPageNo(1);
        }
        if (query.getPageSize() == null || query.getPageSize() < 1) {
            query.setPageSize(50);
        }
        return query;
    }

    /** 体检诊断建议库固定使用 {@code peis_dx}，与院内诊断隔离。 */
    private String peisDiagnosisSource() {
        return DEFAULT_DIAGNOSIS_SOURCE;
    }

    private void validateSaveDiagnosisTemplateRequest(SaveDiagnosisTemplateRequest request) {
        if (request == null) {
            throw new ValidationException("保存诊断模板失败：请求不能为空");
        }
        if (StringUtils.isEmpty(request.getCode())) {
            throw new ValidationException("保存诊断模板失败：编码不能为空");
        }
        if (StringUtils.isEmpty(request.getDisplay())) {
            throw new ValidationException("保存诊断模板失败：名称不能为空");
        }
        if (StringUtils.isEmpty(request.getStatusEnum())) {
            throw new ValidationException("保存诊断模板失败：状态不能为空");
        }
    }

    private void validateSaveCommonResultRequest(SaveCommonResultRequest request) {
        if (request == null) {
            throw new ValidationException("保存常见结果失败：请求不能为空");
        }
        if (StringUtils.isEmpty(request.getDisplay())) {
            throw new ValidationException("保存常见结果失败：名称不能为空");
        }
        if (StringUtils.isEmpty(request.getValueString())) {
            throw new ValidationException("保存常见结果失败：结果值不能为空");
        }
        if (StringUtils.isEmpty(request.getStatusEnum())) {
            throw new ValidationException("保存常见结果失败：状态不能为空");
        }
    }
}
