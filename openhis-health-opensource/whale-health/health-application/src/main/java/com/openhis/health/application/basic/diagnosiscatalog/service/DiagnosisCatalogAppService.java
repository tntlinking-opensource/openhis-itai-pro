/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.basic.diagnosiscatalog.service;

import com.openhis.health.application.common.pagination.PageQueryBinder;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.application.basic.diagnosiscatalog.dto.DiagnosisCatalogDetailDto;
import com.openhis.health.application.basic.diagnosiscatalog.dto.DiagnosisCatalogDto;
import com.openhis.health.application.basic.diagnosiscatalog.dto.DiagnosisCatalogInitDto;
import com.openhis.health.application.basic.diagnosiscatalog.mapper.DiagnosisCatalogMapper;
import com.openhis.health.application.basic.diagnosiscatalog.request.CreateDiagnosisCatalogRequest;
import com.openhis.health.application.basic.diagnosiscatalog.request.QueryDiagnosisCatalogRequest;
import com.openhis.health.application.basic.diagnosiscatalog.request.UpdateDiagnosisCatalogRequest;
import com.openhis.health.domain.clinical.conditiondefinition.command.EditDiseaseStartCommand;
import com.openhis.health.domain.clinical.conditiondefinition.command.EditDiseaseStopCommand;
import com.openhis.health.domain.clinical.conditiondefinition.queries.DiseasePageQuery;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.DiseasePageItemQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionDomainService;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

    /**
     * 返回疾病目录维护页所需的下拉：分类、发布状态、医保诊断类型。
     *
     * @return 初始化 DTO，三项列表均由术语或枚举选项填充
     */
    public DiagnosisCatalogInitDto init() {
        DiagnosisCatalogInitDto dto = new DiagnosisCatalogInitDto();
        dto.setDiseaseCategoryList(codeSystemQueryService.getOptionListByCodeSystemName(CONDITION_SOURCE_CODE_SYSTEM));
        dto.setStatusFlagOptions(codeSystemQueryService.getOptionListByEnum(PublicationStatus.class));
        dto.setYbDiagTypes(codeSystemQueryService.getOptionListByCodeSystemName(DIAG_TYPE_CODE_SYSTEM));
        return dto;
    }

    /**
     * 分页查询疾病目录：关键字与来源分类下推到领域分页查询。
     *
     * @param query 分页请求；{@code null} 时使用第 1 页、每页 50 条
     * @return 分页列表 DTO
     */
    public PagedResult<DiagnosisCatalogDto> pageDiagnosisCatalog(QueryDiagnosisCatalogRequest query) {
        QueryDiagnosisCatalogRequest request = ensureQuery(query);
        // 步骤一：将 HTTP 分页字段并入领域 DiseasePageQuery
        DiseasePageQuery pageQuery = new DiseasePageQuery();
        PageQueryBinder.bind(request, pageQuery);
        pageQuery.setSourceEnum(request.getSourceEnum());

        int pageSize;
        if (request.getPageSize() != null && request.getPageSize() > 0) {
            pageSize = request.getPageSize();
        } else {
            pageSize = 50;
        }

        // Spring Data 页下标 0-based：AbstractPageQuery#springPageIndex()
        Page<DiseasePageItemQueryModel> page = conditionDefinitionQueryService.getDiseaseList(
            pageQuery,
            PageRequest.of(request.springPageIndex(), pageSize));
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
        validateCreateRequest(request);
        // 新建默认「生效」，与列表筛选及停用路径语义一致
        if (request.getStatusEnum() == null || request.getStatusEnum().isBlank()) {
            request.setStatusEnum(PublicationStatus.ACTIVE.getCode());
        }
        // 步骤一：领域落库；步骤二：回读详情形状供编辑页直接复用
        Long id = conditionDefinitionDomainService.addDisease(diagnosisMapper.toCreateCommand(request));
        var detail = conditionDefinitionQueryService.getDiseaseOne(id);
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
        validateUpdateRequest(request);
        conditionDefinitionDomainService.editDisease(diagnosisMapper.toUpdateCommand(request));
        var detail = conditionDefinitionQueryService.getDiseaseOne(request.getId());
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
        if (id == null) {
            throw new ValidationException("查询疾病目录失败：id 不能为空");
        }
        return diagnosisMapper.toDetailDto(conditionDefinitionQueryService.getDiseaseOne(id));
    }

    /**
     * 单条停用/下线疾病目录（领域批量停用语义，此处单元素列表）。
     *
     * @param id 主键
     * @throws ValidationException id 为空时
     */
    public void deleteDiagnosisCatalog(Long id) {
        if (id == null) {
            throw new ValidationException("删除疾病目录失败：id 不能为空");
        }
        EditDiseaseStopCommand command = new EditDiseaseStopCommand();
        command.setIds(List.of(id));
        conditionDefinitionDomainService.editDiseaseStop(command);
    }

    /**
     * 批量启用（发布）疾病目录项。
     *
     * @param ids 主键列表，须非空
     * @throws ValidationException ids 为空时
     */
    public void batchEnable(List<Long> ids) {
        requireIds(ids, "启用疾病目录失败：ids 不能为空");
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
        requireIds(ids, "停用疾病目录失败：ids 不能为空");
        EditDiseaseStopCommand command = new EditDiseaseStopCommand();
        command.setIds(ids);
        conditionDefinitionDomainService.editDiseaseStop(command);
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
