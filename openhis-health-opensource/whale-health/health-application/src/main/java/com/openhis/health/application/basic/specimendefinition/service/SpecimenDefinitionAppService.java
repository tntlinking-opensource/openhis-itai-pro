/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.specimendefinition.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.application.basic.specimendefinition.dto.SpecimenDefinitionDto;
import com.openhis.health.application.basic.specimendefinition.dto.SpecimenDefinitionInitDto;
import com.openhis.health.application.basic.specimendefinition.mapper.SpecimenDefinitionMapper;
import com.openhis.health.application.basic.specimendefinition.request.CreateSpecimenDefinitionRequest;
import com.openhis.health.application.basic.specimendefinition.request.PageSpecimenDefinitionRequest;
import com.openhis.health.application.basic.specimendefinition.request.SearchSpecimenDefinitionRequest;
import com.openhis.health.application.basic.specimendefinition.request.UpdateSpecimenDefinitionRequest;
import com.openhis.health.domain.administration.specimendefinition.command.ActivateSpecimenDefinitionsCommand;
import com.openhis.health.domain.administration.specimendefinition.command.DeactivateSpecimenDefinitionsCommand;
import com.openhis.health.domain.administration.specimendefinition.command.DeleteSpecimenDefinitionCommand;
import com.openhis.health.domain.administration.specimendefinition.queries.PageSpecimenDefinitionQuery;
import com.openhis.health.domain.administration.specimendefinition.queries.SearchSpecimenDefinitionQuery;
import com.openhis.health.domain.administration.specimendefinition.queries.model.SpecimenDefinitionDetailQueryModel;
import com.openhis.health.domain.administration.specimendefinition.queries.model.SpecimenDefinitionSearchItemQueryModel;
import com.openhis.health.domain.administration.specimendefinition.service.SpecimenDefinitionDomainService;
import com.openhis.health.domain.administration.specimendefinition.service.SpecimenDefinitionQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.enums.SpecimenType;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.EntityNotFoundException;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 基础数据「样本定义」应用服务：维护页初始化、分页查询、增删改、批量启停与关键字检索。
 *
 * <p>编排 {@link SpecimenDefinitionQueryService} 与 {@link SpecimenDefinitionDomainService}；
 * 当前阶段不联动诊疗项目（ActivityDefinition）引用校验。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpecimenDefinitionAppService {

    private final SpecimenDefinitionQueryService specimenDefinitionQueryService;
    private final SpecimenDefinitionDomainService specimenDefinitionDomainService;
    private final SpecimenDefinitionMapper specimenDefinitionMapper;
    private final CodeSystemQueryService codeSystemQueryService;
    /** 写路径操作人门控（B.6.5），禁止 AppService 直读 ICurrentUser 或写死 operatorId。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /**
     * 返回样本定义维护页所需的下拉：标本种类、发布状态。
     *
     * @return 初始化 DTO
     */
    public SpecimenDefinitionInitDto init() {
        SpecimenDefinitionInitDto dto = new SpecimenDefinitionInitDto();
        // Pre：从共享枚举生成下拉，保证与领域校验口径一致
        dto.setTypeEnumOptions(codeSystemQueryService.getOptionListByEnum(SpecimenType.class));
        dto.setStatusEnumOptions(codeSystemQueryService.getOptionListByEnum(PublicationStatus.class));
        return dto;
    }

    /**
     * 分页查询样本定义列表。
     *
     * @param request 分页与筛选条件；{@code null} 时使用默认第 1 页、每页 50 条
     * @return 分页 DTO 结果
     */
    public PagedResult<SpecimenDefinitionDto> page(PageSpecimenDefinitionRequest request) {
        PageSpecimenDefinitionRequest safeRequest = ensurePageRequest(request);
        // Gate：归一化分页窗口，避免非法页码导致读侧异常
        int pageSize = 50;
        if (safeRequest.getPageSize() != null && safeRequest.getPageSize() > 0) {
            pageSize = safeRequest.getPageSize();
        }
        // Pre：HTTP 请求转为领域分页查询入参
        PageSpecimenDefinitionQuery query = new PageSpecimenDefinitionQuery();
        query.setKeyword(trimToNull(safeRequest.getKeyword()));
        query.setStatusEnum(trimToNull(safeRequest.getStatusEnum()));
        query.setTypeEnum(trimToNull(safeRequest.getTypeEnum()));
        // Cmd：单次 SQL 分页查询，排序默认由读服务补齐
        Page<SpecimenDefinitionDto> page = specimenDefinitionQueryService.pageSpecimenDefinition(
            query,
            PageRequest.of(safeRequest.springPageIndex(), pageSize))
            .map(specimenDefinitionMapper::toDto);
        // Asm：Spring Page 转为统一分页响应
        return PagedResult.fromPage(page);
    }

    /**
     * 按主键查询样本定义详情。
     *
     * @param id 主键
     * @return 详情 DTO
     * @throws ValidationException id 为空时
     * @throws EntityNotFoundException 记录不存在或已删除时
     */
    public SpecimenDefinitionDto getSpecimenDefinition(Long id) {
        if (id == null) {
            throw new ValidationException("查询样本定义失败：id 不能为空");
        }
        // Pre：读侧按主键装载详情读模型
        SpecimenDefinitionDetailQueryModel model = specimenDefinitionQueryService.getSpecimenDefinition(id);
        if (model == null) {
            throw new EntityNotFoundException("样本定义不存在或已删除");
        }
        // Asm：读模型投影为对外 DTO
        return specimenDefinitionMapper.toDto(model);
    }

    /**
     * 新建样本定义并回读详情。
     *
     * @param request 创建请求
     * @return 新建后详情 DTO
     * @throws ValidationException 参数不合法时
     */
    public SpecimenDefinitionDto createSpecimenDefinition(CreateSpecimenDefinitionRequest request) {
        validateCreateRequest(request);
        // Cmd：请求转命令并调用领域写服务
        Long id = specimenDefinitionDomainService.createSpecimenDefinition(
            specimenDefinitionMapper.toCreateCommand(request));
        log.info("Created specimen definition: {}", id);
        return getSpecimenDefinition(id);
    }

    /**
     * 维护样本定义并回读详情。
     *
     * @param request 更新请求
     * @return 更新后详情 DTO
     * @throws ValidationException 参数不合法时
     */
    public SpecimenDefinitionDto updateSpecimenDefinition(UpdateSpecimenDefinitionRequest request) {
        validateUpdateRequest(request);
        // Cmd：下发维护命令，状态迁移由领域不变式保证
        specimenDefinitionDomainService.updateSpecimenDefinition(
            specimenDefinitionMapper.toUpdateCommand(request));
        log.info("Updated specimen definition: {}", request.getId());
        return getSpecimenDefinition(request.getId());
    }

    /**
     * 逻辑删除样本定义（当前不校验诊疗项目引用）。
     *
     * @param id 主键
     * @throws ValidationException id 为空时
     */
    public void deleteSpecimenDefinition(Long id) {
        if (id == null) {
            throw new ValidationException("删除样本定义失败：id 不能为空");
        }
        // Gate：删除须携带当前登录操作人，用于软删审计字段
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.SPECIMEN_DEFINITION_INVALID_ARGUMENT,
            "请先登录后再删除样本定义");
        // Cmd：批量命令形态下单条删除
        DeleteSpecimenDefinitionCommand command = new DeleteSpecimenDefinitionCommand();
        command.setOperatorId(operatorId);
        DeleteSpecimenDefinitionCommand.DeleteSpecimenDefinitionItemCommand item =
            new DeleteSpecimenDefinitionCommand.DeleteSpecimenDefinitionItemCommand();
        item.setId(id);
        command.setItems(Collections.singletonList(item));
        specimenDefinitionDomainService.deleteSpecimenDefinition(command);
        log.info("Deleted specimen definition: {}", id);
    }

    /**
     * 启用单条样本定义。
     *
     * @param id 主键
     * @return 启用后详情 DTO
     * @throws ValidationException id 为空时
     */
    public SpecimenDefinitionDto enableSpecimenDefinition(Long id) {
        if (id == null) {
            throw new ValidationException("启用样本定义失败：id 不能为空");
        }
        // Cmd：复用批量启用路径，保证状态迁移口径一致
        batchEnable(Collections.singletonList(id));
        return getSpecimenDefinition(id);
    }

    /**
     * 停用单条样本定义。
     *
     * @param id 主键
     * @return 停用后详情 DTO
     * @throws ValidationException id 为空时
     */
    public SpecimenDefinitionDto disableSpecimenDefinition(Long id) {
        if (id == null) {
            throw new ValidationException("停用样本定义失败：id 不能为空");
        }
        // Cmd：复用批量停用路径，保证状态迁移口径一致
        batchDisable(Collections.singletonList(id));
        return getSpecimenDefinition(id);
    }

    /**
     * 批量启用样本定义。
     *
     * @param ids 主键列表，须非空
     * @throws ValidationException ids 为空时
     */
    public void batchEnable(List<Long> ids) {
        requireIds(ids, "启用样本定义失败：ids 不能为空");
        // Cmd：组装批量启用命令并下发领域写服务
        ActivateSpecimenDefinitionsCommand command = new ActivateSpecimenDefinitionsCommand();
        List<ActivateSpecimenDefinitionsCommand.ActivateSpecimenDefinitionItemCommand> items = new ArrayList<>();
        for (Long id : ids) {
            ActivateSpecimenDefinitionsCommand.ActivateSpecimenDefinitionItemCommand item =
                new ActivateSpecimenDefinitionsCommand.ActivateSpecimenDefinitionItemCommand();
            item.setId(id);
            items.add(item);
        }
        command.setItems(items);
        specimenDefinitionDomainService.activateSpecimenDefinitions(command);
    }

    /**
     * 批量停用样本定义。
     *
     * @param ids 主键列表，须非空
     * @throws ValidationException ids 为空时
     */
    public void batchDisable(List<Long> ids) {
        requireIds(ids, "停用样本定义失败：ids 不能为空");
        // Cmd：组装批量停用命令并下发领域写服务
        DeactivateSpecimenDefinitionsCommand command = new DeactivateSpecimenDefinitionsCommand();
        List<DeactivateSpecimenDefinitionsCommand.DeactivateSpecimenDefinitionItemCommand> items = new ArrayList<>();
        for (Long id : ids) {
            DeactivateSpecimenDefinitionsCommand.DeactivateSpecimenDefinitionItemCommand item =
                new DeactivateSpecimenDefinitionsCommand.DeactivateSpecimenDefinitionItemCommand();
            item.setId(id);
            items.add(item);
        }
        command.setItems(items);
        specimenDefinitionDomainService.deactivateSpecimenDefinitions(command);
    }

    /**
     * 关键字检索样本定义，供下拉选品；默认仅返回启用态。
     *
     * @param request 检索条件，可空
     * @return 命中列表
     */
    public List<SpecimenDefinitionDto> search(SearchSpecimenDefinitionRequest request) {
        SearchSpecimenDefinitionRequest safeRequest = request;
        if (safeRequest == null) {
            safeRequest = new SearchSpecimenDefinitionRequest();
        }
        // Pre：HTTP 检索入参转为领域查询对象
        SearchSpecimenDefinitionQuery query = new SearchSpecimenDefinitionQuery();
        query.setKeyword(trimToNull(safeRequest.getKeyword()));
        query.setStatusEnum(trimToNull(safeRequest.getStatusEnum()));
        query.setTypeEnum(trimToNull(safeRequest.getTypeEnum()));
        query.setLimit(safeRequest.getLimit());
        List<SpecimenDefinitionSearchItemQueryModel> models = specimenDefinitionQueryService.searchByKeyWord(query);
        // Asm：检索行批量投影为 DTO
        List<SpecimenDefinitionDto> result = new ArrayList<>();
        for (SpecimenDefinitionSearchItemQueryModel model : models) {
            SpecimenDefinitionDto dto = specimenDefinitionMapper.toDto(model);
            if (dto != null) {
                result.add(dto);
            }
        }
        return result;
    }

    /**
     * 分页请求空值回落：默认第 1 页、每页 50 条。
     *
     * @param request 原始分页请求
     * @return 非空分页请求
     */
    private PageSpecimenDefinitionRequest ensurePageRequest(PageSpecimenDefinitionRequest request) {
        if (request != null) {
            return request;
        }
        PageSpecimenDefinitionRequest safeRequest = new PageSpecimenDefinitionRequest();
        safeRequest.setPageNo(1);
        safeRequest.setPageSize(50);
        return safeRequest;
    }

    /**
     * 新建样本定义入参校验：名称与标本种类不可空。
     *
     * @param request 新建请求
     * @throws ValidationException 必填缺失
     */
    private void validateCreateRequest(CreateSpecimenDefinitionRequest request) {
        if (request == null) {
            throw new ValidationException("新建样本定义失败：请求体不能为空");
        }
        if (StringUtils.isEmpty(request.getName())) {
            throw new ValidationException("新建样本定义失败：样本名称不能为空");
        }
        if (StringUtils.isEmpty(request.getTypeEnum())) {
            throw new ValidationException("新建样本定义失败：标本种类不能为空");
        }
    }

    /**
     * 维护样本定义入参校验：主键、名称与标本种类不可空。
     *
     * @param request 更新请求
     * @throws ValidationException 必填缺失
     */
    private void validateUpdateRequest(UpdateSpecimenDefinitionRequest request) {
        if (request == null || request.getId() == null) {
            throw new ValidationException("维护样本定义失败：id 不能为空");
        }
        if (StringUtils.isEmpty(request.getName())) {
            throw new ValidationException("维护样本定义失败：样本名称不能为空");
        }
        if (StringUtils.isEmpty(request.getTypeEnum())) {
            throw new ValidationException("维护样本定义失败：标本种类不能为空");
        }
    }

    /**
     * 批量操作前置：主键列表不可空。
     *
     * @param ids          待操作主键
     * @param errorMessage 为空时 ValidationException 文案
     * @throws ValidationException ids 为空
     */
    private void requireIds(List<Long> ids, String errorMessage) {
        if (ids == null || ids.isEmpty()) {
            throw new ValidationException(errorMessage);
        }
    }
}
