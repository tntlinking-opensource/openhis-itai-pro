/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.executelocation.service;

import com.openhis.health.application.basic.executelocation.dto.ExecuteLocationDto;
import com.openhis.health.application.basic.executelocation.dto.ExecuteLocationInitDto;
import com.openhis.health.application.basic.executelocation.mapper.ExecuteLocationMapper;
import com.openhis.health.application.basic.executelocation.request.BatchCreateExecuteLocationRequest;
import com.openhis.health.application.basic.executelocation.request.CreateExecuteLocationRequest;
import com.openhis.health.application.basic.executelocation.request.QueryActivityDefinitionForExecuteLocationRequest;
import com.openhis.health.application.basic.executelocation.request.QueryExecuteLocationRequest;
import com.openhis.health.application.basic.executelocation.request.UpdateExecuteLocationRequest;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.shared.advice.OrderCatalogDisplayAssembler;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogPageLoader;
import com.openhis.health.application.shared.product.ProductDefinitionPageQuery;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.share.enums.OrganizationType;
import com.openhis.health.domain.custom.activityexecutionlocation.command.CreateExecutionRuleCommand;
import com.openhis.health.domain.custom.activityexecutionlocation.command.DeleteExecutionRuleCommand;
import com.openhis.health.domain.custom.activityexecutionlocation.command.UpdateExecutionRuleCommand;
import com.openhis.health.domain.custom.activityexecutionlocation.queries.model.ExecutionRuleListQueryModel;
import com.openhis.health.domain.custom.activityexecutionlocation.service.ActivityExecutionLocationDomainService;
import com.openhis.health.domain.custom.activityexecutionlocation.service.ActivityExecutionLocationQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基础数据「执行科室」配置应用服务：WH-EXE001 按开立科室维护诊疗项目执行规则（行内选执行科室，对齐取药科室）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>{@link ActivityExecutionLocationQueryService}：按开立科室列出规则（配置读侧）</li>
 *   <li>{@link ActivityExecutionLocationDomainService}：单条/批量 CRUD</li>
 *   <li>{@link ProductDefinitionCatalogPageLoader}：挑选待绑定的诊疗活动（复用既有目录读能力，不新增查询 IF）</li>
 *   <li>{@link ProductDefinitionDisplayTerminologyAssembler}：列表 enrich 诊疗项目名称</li>
 * </ul>
 *
 * <p>接入方式：由 {@code AppServiceControllerRegistrar} 注册为
 * {@code POST /api/app/executeLocation/{methodName}}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecuteLocationAppService {

    /** 执行规则请求/读模型与 DTO、领域命令映射。 */
    private final ExecuteLocationMapper executeLocationMapper;
    /** 执行规则读侧。 */
    private final ActivityExecutionLocationQueryService activityExecutionLocationQueryService;
    /** 执行规则写侧。 */
    private final ActivityExecutionLocationDomainService activityExecutionLocationDomainService;
    /** 写路径操作人门控 B.6.1。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;
    /** 组织机构读侧：科室树与名称解析。 */
    private final OrganizationQueryService organizationQueryService;
    /** 位置读侧：执行地点名称 enrich。 */
    private final LocationQueryService locationQueryService;
    /** 术语下拉与码→文案映射。 */
    private final TerminologyOptionLoader terminologyOptionLoader;
    /** 诊疗活动定义分页：挑选待绑定项目。 */
    private final ProductDefinitionCatalogPageLoader productDefinitionCatalogPageLoader;
    /** 诊疗活动定义批量展示加载。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /**
     * 返回执行科室配置页初始化选项：开立科室树、执行科室树、诊疗大类。
     *
     * @return 初始化 DTO
     */
    public ExecuteLocationInitDto init() {
        // Gate：只读页初始化，不写库
        // Pre：开立科室用完整机构树（左侧导航）；执行科室仅 DEPARTMENT，排除医院根
        // Asm：映射页初始化响应
        ExecuteLocationInitDto dto = new ExecuteLocationInitDto();
        dto.setOrganizationOptions(this.buildOrganizationOptions());
        dto.setExecutionOrgOptions(this.buildDepartmentExecutionOrgOptions());
        dto.setActivityCategoryOptions(
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.ACTIVITY_CATEGORY_CODE));
        dto.setExecutionLocationOptions(Collections.emptyList());
        return dto;
    }

    /**
     * 分页检索可供绑定的诊疗活动定义，复用产品定义读模型（{@code wkf_activity_definition}）。
     *
     * @param request 分页条件，须含开立科室与诊疗大类
     * @return 分页选项；value 为产品定义主键字符串
     * @throws BusinessException 必填参数缺失时
     */
    public PagedResult<OptionDto> pageActivityDefinitionsForExecution(
        QueryActivityDefinitionForExecuteLocationRequest request
    ) {
        // Gate：开立科室与诊疗大类必填
        if (request == null || request.getOrganizationId() == null) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "开立科室ID不能为空");
        }
        if (request.getActivityCategoryCode() == null || request.getActivityCategoryCode().isBlank()) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "诊疗大类不能为空");
        }
        // Pre：装配产品定义分页查询
        ProductDefinitionPageQuery query = new ProductDefinitionPageQuery();
        query.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        query.setCategoryCode(request.getActivityCategoryCode());
        PageQueryBinder.bind(request, query);
        query.setPageSize(request.getBoundedPageSize());
        Page<ProductDefinitionDisplayDto> page = productDefinitionCatalogPageLoader.pageProductDefinition(query);
        // Asm：映射为分页选项
        Page<OptionDto> optionPage = page.map(item -> new OptionDto(
            item.getProductDefinitionId(),
            resolveProductDefinitionIdValue(item),
            item.getProductName()
        ));
        return PagedResult.fromPage(optionPage);
    }

    /**
     * 按开立科室（及可选诊疗大类）列出已配置执行规则，并在应用层 enrich 展示名。
     *
     * <p><b>规模说明</b>：当前实现先拉全量规则再在内存 {@code subList} 分页；规则数量显著增大时应改为查询层数据库分页。</p>
     *
     * @param request 查询请求，须含 {@code organizationId}
     * @return 内存分页后的 DTO 页
     * @throws BusinessException 开立科室 id 缺失或不存在时
     */
    public PagedResult<ExecuteLocationDto> listByOrganizationId(QueryExecuteLocationRequest request) {
        // Gate：开立科室 id 必填且须存在
        if (request == null || request.getOrganizationId() == null) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "开立科室ID不能为空");
        }
        Organization organization = organizationQueryService.loadOrganizationByIdForDisplay(request.getOrganizationId());
        if (organization == null) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "开立科室不存在");
        }
        // Pre：拉取该开立科室下全部规则并在内存分页
        List<ExecutionRuleListQueryModel> rows = activityExecutionLocationQueryService.listExecutionRulesByOrganization(
            request.getOrganizationId(),
            request.getActivityCategoryCode()
        );
        List<ExecuteLocationDto> content = new ArrayList<>();
        for (ExecutionRuleListQueryModel row : rows) {
            content.add(executeLocationMapper.toDto(row));
        }
        this.enrichDtoList(content, organization.getName());

        // Asm：内存 subList 分页后返回
        int pageNum = 1;
        if (request.getPageNo() != null && request.getPageNo() > 0) {
            pageNum = request.getPageNo();
        }
        int pageSize = 20;
        if (request.getPageSize() != null && request.getPageSize() > 0) {
            pageSize = request.getPageSize();
        }
        int from = Math.min((pageNum - 1) * pageSize, content.size());
        int to = Math.min(from + pageSize, content.size());
        Page<ExecuteLocationDto> page = new PageImpl<>(
            content.subList(from, to),
            PageRequest.of(pageNum - 1, pageSize),
            content.size()
        );
        return PagedResult.fromPage(page);
    }

    /**
     * 创建单条执行规则，返回领域生成的主键列表中的第一条。
     *
     * @param request 创建请求
     * @return 新建规则主键；若领域返回空列表则 {@code null}
     * @throws BusinessException 请求体非法或校验失败时
     */
    public Long create(CreateExecuteLocationRequest request) {
        // Gate：请求体、开立科室、诊疗大类、执行科室与写路径公共校验
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "创建请求不能为空");
        }
        if (request.getActivityCategoryCode() == null || request.getActivityCategoryCode().isBlank()) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "诊疗大类不能为空");
        }
        this.validateWriteRequest(
            request.getOrganizationId(),
            request.getExecutionOrgId(),
            request.getStartTime(),
            request.getEndTime());
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT,
            "请先登录后再保存执行科室规则");
        // Cmd：创建单条执行规则
        CreateExecutionRuleCommand command = new CreateExecutionRuleCommand();
        command.setOrganizationId(request.getOrganizationId());
        command.setExecutionOrgId(request.getExecutionOrgId());
        command.setActivityCategoryCode(request.getActivityCategoryCode());
        command.setOperatorId(operatorId);
        List<CreateExecutionRuleCommand.CreateExecutionRuleItemCommand> items = new ArrayList<>();
        items.add(executeLocationMapper.toCreateItem(request));
        command.setRules(items);
        List<Long> ids = activityExecutionLocationDomainService.createExecutionRule(command);
        // Branch：领域返回空列表时无新建主键
        if (ids.isEmpty()) {
            return null;
        }
        return ids.get(0);
    }

    /**
     * 更新单条执行规则。
     *
     * @param request 更新请求
     * @throws BusinessException id 缺失或校验失败时
     */
    public void update(UpdateExecuteLocationRequest request) {
        // Gate：主键非空与写路径公共校验
        if (request == null || request.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "ID不能为空");
        }
        this.validateWriteRequest(
            request.getOrganizationId(),
            request.getExecutionOrgId(),
            request.getStartTime(),
            request.getEndTime());
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT,
            "请先登录后再保存执行科室规则");
        UpdateExecutionRuleCommand command = executeLocationMapper.toUpdateCommand(request);
        command.setOperatorId(operatorId);
        // Cmd：更新执行规则
        activityExecutionLocationDomainService.updateExecutionRule(command);
    }

    /**
     * 按主键删除执行规则。
     *
     * @param id 主键
     * @throws BusinessException id 为空时
     */
    public void deleteById(Long id) {
        // Gate：主键非空且须已登录操作人
        if (id == null) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "ID不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT,
            "请先登录后再删除执行科室规则");
        DeleteExecutionRuleCommand command = new DeleteExecutionRuleCommand();
        command.setId(id);
        command.setOperatorId(operatorId);
        command.setDeleteReason("应用层删除执行规则");
        // Cmd：逻辑删除执行规则
        activityExecutionLocationDomainService.deleteExecutionRule(command);
    }

    /**
     * 批量创建执行规则：将多条诊疗定义展开为同一时段/地点的子命令后一次性调用领域批量创建。
     *
     * @param request 批量请求
     * @throws BusinessException 科室、大类或诊疗定义列表不合法时
     */
    public void batchCreate(BatchCreateExecuteLocationRequest request) {
        // Gate：开立科室、执行科室、大类、诊疗定义列表与写路径公共校验
        if (request == null || request.getOrganizationId() == null) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "开立科室ID不能为空");
        }
        if (request.getExecutionOrgId() == null) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "执行科室ID不能为空");
        }
        if (request.getActivityCategoryCode() == null || request.getActivityCategoryCode().isBlank()) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "活动类别不能为空");
        }
        if (request.getActivityDefinitionIds() == null || request.getActivityDefinitionIds().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "诊疗定义列表不能为空");
        }
        this.validateWriteRequest(
            request.getOrganizationId(),
            request.getExecutionOrgId(),
            request.getStartTime(),
            request.getEndTime());
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT,
            "请先登录后再保存执行科室规则");
        // Cmd：批量展开为子命令后一次性创建
        CreateExecutionRuleCommand command = new CreateExecutionRuleCommand();
        command.setOrganizationId(request.getOrganizationId());
        command.setExecutionOrgId(request.getExecutionOrgId());
        command.setActivityCategoryCode(request.getActivityCategoryCode());
        command.setOperatorId(operatorId);
        List<CreateExecutionRuleCommand.CreateExecutionRuleItemCommand> items = new ArrayList<>();
        for (Long definitionId : request.getActivityDefinitionIds()) {
            if (definitionId == null) {
                throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "诊疗定义不能为空");
            }
            CreateExecuteLocationRequest single = new CreateExecuteLocationRequest();
            single.setOrganizationId(request.getOrganizationId());
            single.setExecutionOrgId(request.getExecutionOrgId());
            single.setActivityCategoryCode(request.getActivityCategoryCode());
            single.setActivityDefinitionId(definitionId);
            single.setExecutionLocationId(request.getExecutionLocationId());
            single.setStartTime(request.getStartTime());
            single.setEndTime(request.getEndTime());
            single.setPriority(request.getPriority());
            single.setDisplayOrder(request.getDisplayOrder());
            single.setRemark(request.getRemark());
            items.add(executeLocationMapper.toCreateItem(single));
        }
        command.setRules(items);
        activityExecutionLocationDomainService.createExecutionRule(command);
    }

    /**
     * 写路径公共校验：开立科室与执行科室均须存在、时段合法。
     *
     * @param organizationId 开立科室
     * @param executionOrgId 执行科室
     * @param startTime 开始时间字符串
     * @param endTime 结束时间字符串
     */
    private void validateWriteRequest(
        Long organizationId,
        Long executionOrgId,
        String startTime,
        String endTime
    ) {
        if (organizationId == null) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "开立科室ID不能为空");
        }
        Organization orderingOrg = organizationQueryService.loadOrganizationByIdForDisplay(organizationId);
        if (orderingOrg == null) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "开立科室不存在");
        }
        if (executionOrgId == null) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "执行科室ID不能为空");
        }
        Organization executionOrg = organizationQueryService.loadOrganizationByIdForDisplay(executionOrgId);
        if (executionOrg == null) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "执行科室不存在");
        }
        // 医院根节点不是科室，禁止写入执行科室
        if (!OrganizationType.DEPARTMENT.getCode().equals(executionOrg.getTypeEnum())) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "执行科室须为科室节点，不能选择医院");
        }
        if (StringUtils.isNotEmpty(startTime) && StringUtils.isNotEmpty(endTime)) {
            java.time.LocalTime start = executeLocationMapper.parseTime(startTime);
            java.time.LocalTime end = executeLocationMapper.parseTime(endTime);
            if (start != null && end != null && !start.isBefore(end)) {
                throw new BusinessException(
                    ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT,
                    "生效开始时间必须早于结束时间");
            }
        }
    }

    /**
     * 批量 enrich 规则行展示字段（开立/执行科室名、地点名、诊疗大类文案、诊疗项目名称）。
     *
     * @param rows 待 enrich 列表
     * @param organizationName 当前选中开立科室名称
     */
    private void enrichDtoList(List<ExecuteLocationDto> rows, String organizationName) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> locationIds = new HashSet<>();
        Set<Long> executionOrgIds = new HashSet<>();
        List<ProductDefinitionBatchLookup> productLookups = new ArrayList<>();
        for (ExecuteLocationDto row : rows) {
            if (row == null) {
                continue;
            }
            row.setOrganizationName(organizationName);
            if (row.getExecutionOrgId() != null) {
                executionOrgIds.add(row.getExecutionOrgId());
            }
            if (row.getExecutionLocationId() != null) {
                locationIds.add(row.getExecutionLocationId());
            }
            if (row.getActivityDefinitionId() != null) {
                ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
                lookup.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
                lookup.setProductDefinitionId(row.getActivityDefinitionId());
                productLookups.add(lookup);
            }
        }
        Map<Long, String> executionOrgNameById = organizationQueryService.mapNamesByIds(executionOrgIds);
        Map<Long, String> locationNameById = locationQueryService.mapNamesByIds(locationIds);
        Map<String, ProductDefinitionDisplayDto> productByKey = productDefinitionDisplayTerminologyAssembler.loadAsMap(productLookups);
        Map<String, String> activityCategoryLabels =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.ACTIVITY_CATEGORY_CODE);
        for (ExecuteLocationDto row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getExecutionOrgId() != null) {
                row.setExecutionOrgName(executionOrgNameById.get(row.getExecutionOrgId()));
            }
            if (row.getExecutionLocationId() != null) {
                row.setExecutionLocationName(locationNameById.get(row.getExecutionLocationId()));
            }
            row.setActivityCategoryCode_dictText(
                OrderCatalogDisplayAssembler.resolveCategoryDisplay(activityCategoryLabels, row.getActivityCategoryCode()));
            if (row.getActivityDefinitionId() != null) {
                String key = ProductDefinitionKeyUtil.buildCompositeKey(
                    TableNameConstants.WKF_ACTIVITY_DEFINITION, row.getActivityDefinitionId());
                ProductDefinitionDisplayDto product = productByKey.get(key);
                if (product != null) {
                    row.setActivityDefinitionName(product.getProductName());
                }
            }
        }
    }

    /**
     * 将扁平组织机构列表组装为树形 {@link OptionDto}（含医院根，供左侧开立科室导航）。
     *
     * @return 树根选项列表
     */
    private List<OptionDto> buildOrganizationOptions() {
        List<Organization> organizations = organizationQueryService.getOrganizationDictTree();
        if (organizations == null || organizations.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, List<Organization>> childrenByParentId = new HashMap<>();
        Set<Long> organizationIdSet = new HashSet<>();
        for (Organization organization : organizations) {
            if (organization == null || organization.getId() == null) {
                continue;
            }
            organizationIdSet.add(organization.getId());
            List<Organization> children = childrenByParentId.computeIfAbsent(
                organization.getParentId(), ignored -> new ArrayList<>());
            children.add(organization);
        }
        List<Organization> rootOrganizations = new ArrayList<>();
        for (Organization organization : organizations) {
            if (organization == null || organization.getId() == null) {
                continue;
            }
            Long parentId = organization.getParentId();
            if (parentId == null || !organizationIdSet.contains(parentId)) {
                rootOrganizations.add(organization);
            }
        }
        return buildOrganizationOptionTree(rootOrganizations, childrenByParentId);
    }

    /**
     * 构建执行科室下拉：仅启用科室节点，排除医院根（与责任科室下拉同口径）。
     *
     * @return 扁平科室选项列表
     */
    private List<OptionDto> buildDepartmentExecutionOrgOptions() {
        List<Organization> departments = organizationQueryService.getOrganizationTreeForResponsibilityDept();
        if (departments == null || departments.isEmpty()) {
            return Collections.emptyList();
        }
        List<OptionDto> result = new ArrayList<>();
        long sort = 0L;
        for (Organization organization : departments) {
            if (organization == null || organization.getId() == null) {
                continue;
            }
            sort++;
            result.add(new OptionDto(
                sort,
                String.valueOf(organization.getId()),
                organization.getName(),
                organization.getBusNo(),
                Collections.emptyList()
            ));
        }
        return result;
    }

    /**
     * 递归构建机构树 Option。
     *
     * @param organizations 当前层节点
     * @param childrenByParentId 父 id → 子列表
     * @return 当前层选项列表
     */
    private List<OptionDto> buildOrganizationOptionTree(
        List<Organization> organizations,
        Map<Long, List<Organization>> childrenByParentId
    ) {
        List<OptionDto> result = new ArrayList<>();
        long sort = 0L;
        for (Organization organization : organizations) {
            if (organization == null || organization.getId() == null) {
                continue;
            }
            sort++;
            List<OptionDto> children = Collections.emptyList();
            List<Organization> childOrganizations = childrenByParentId.get(organization.getId());
            if (childOrganizations != null && !childOrganizations.isEmpty()) {
                children = buildOrganizationOptionTree(childOrganizations, childrenByParentId);
            }
            result.add(new OptionDto(
                sort,
                String.valueOf(organization.getId()),
                organization.getName(),
                organization.getBusNo(),
                children
            ));
        }
        return result;
    }

    /**
     * 产品定义选项的 value 字段：使用主键字符串。
     *
     * @param item 读模型行
     * @return 主键字符串或 {@code null}
     */
    private String resolveProductDefinitionIdValue(ProductDefinitionDisplayDto item) {
        if (item.getProductDefinitionId() == null) {
            return null;
        }
        return String.valueOf(item.getProductDefinitionId());
    }
}
