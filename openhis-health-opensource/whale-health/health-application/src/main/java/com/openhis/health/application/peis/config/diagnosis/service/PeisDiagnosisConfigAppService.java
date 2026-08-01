/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.config.diagnosis.service;

import com.openhis.health.application.peis.config.diagnosis.dto.PeisCommonResultDto;
import com.openhis.health.application.peis.config.diagnosis.dto.PeisDiagnosisTemplateDto;
import com.openhis.health.application.peis.config.diagnosis.mapper.PeisDiagnosisConfigMapper;
import com.openhis.health.application.peis.config.diagnosis.request.SaveCommonResultRequest;
import com.openhis.health.application.peis.config.diagnosis.request.SaveDiagnosisTemplateRequest;
import com.openhis.health.application.peis.config.diagnosis.request.SearchCommonResultRequest;
import com.openhis.health.application.peis.config.diagnosis.request.SearchDiagnosisTemplateRequest;
import com.openhis.health.application.peis.config.diagnosis.response.SearchCommonResultResponse;
import com.openhis.health.application.peis.config.diagnosis.response.SearchDiagnosisTemplateResponse;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.domain.clinical.conditiondefinition.queries.DiseasePageQuery;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.DiseaseDetailQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.DiseasePageItemQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionDomainService;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.peis.commonresult.entity.PeisCommonResult;
import com.openhis.health.domain.peis.commonresult.queries.PeisCommonResultQuery;
import com.openhis.health.domain.peis.commonresult.queries.model.PeisCommonResultModel;
import com.openhis.health.domain.peis.commonresult.service.PeisCommonResultDomainService;
import com.openhis.health.domain.peis.commonresult.service.PeisCommonResultQueryService;
import com.openhis.health.domain.share.constants.PeisCatalogConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
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

    /**
     * 分页检索诊断建议库（CFG-08）。
     */
    public SearchDiagnosisTemplateResponse searchDiagnosisTemplate(SearchDiagnosisTemplateRequest request) {
        // Gate：请求体缺省时补默认分页
        SearchDiagnosisTemplateRequest query = ensureDiagnosisRequest(request);
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
        // Gate：请求体缺省时补默认分页
        SearchCommonResultRequest query = ensureCommonResultRequest(request);
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

    private SearchDiagnosisTemplateRequest ensureDiagnosisRequest(SearchDiagnosisTemplateRequest request) {
        if (request != null) {
            return request;
        }
        SearchDiagnosisTemplateRequest defaults = new SearchDiagnosisTemplateRequest();
        defaults.setPageNo(1);
        defaults.setPageSize(50);
        return defaults;
    }

    private SearchCommonResultRequest ensureCommonResultRequest(SearchCommonResultRequest request) {
        if (request != null) {
            return request;
        }
        SearchCommonResultRequest defaults = new SearchCommonResultRequest();
        defaults.setPageNo(1);
        defaults.setPageSize(50);
        return defaults;
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
