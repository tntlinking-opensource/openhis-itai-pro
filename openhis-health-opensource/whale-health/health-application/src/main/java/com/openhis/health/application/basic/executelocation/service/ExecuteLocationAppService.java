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

package com.openhis.health.application.basic.executelocation.service;

import com.openhis.health.application.common.pagination.PageQueryBinder;
import com.openhis.health.application.common.converter.TextValueConverter;
import com.openhis.health.application.basic.executelocation.dto.ExecuteLocationDto;
import com.openhis.health.application.basic.executelocation.dto.ExecuteLocationInitDto;
import com.openhis.health.application.basic.executelocation.mapper.ExecuteLocationMapper;
import com.openhis.health.application.basic.executelocation.request.BatchCreateExecuteLocationRequest;
import com.openhis.health.application.basic.executelocation.request.CreateExecuteLocationRequest;
import com.openhis.health.application.basic.executelocation.request.ExecuteLocationIdValueRequest;
import com.openhis.health.application.basic.executelocation.request.QueryActivityDefinitionForExecuteLocationRequest;
import com.openhis.health.application.basic.executelocation.request.QueryExecuteLocationRequest;
import com.openhis.health.application.basic.executelocation.request.UpdateExecuteLocationRequest;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.custom.activityexecutionlocation.command.CreateExecutionRuleCommand;
import com.openhis.health.domain.custom.activityexecutionlocation.command.DeleteExecutionRuleCommand;
import com.openhis.health.domain.custom.activityexecutionlocation.command.UpdateExecutionRuleCommand;
import com.openhis.health.domain.custom.activityexecutionlocation.queries.model.ExecutionRuleListQueryModel;
import com.openhis.health.domain.custom.activityexecutionlocation.service.ActivityExecutionLocationDomainService;
import com.openhis.health.domain.custom.activityexecutionlocation.service.ActivityExecutionLocationQueryService;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.querysupport.product.ProductDefinitionCatalogPageLoader;
import com.openhis.health.application.querysupport.product.ProductDefinitionPageQuery;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 基础数据「活动执行地点/执行规则」应用服务：维护执行科室与诊疗定义之间的执行位置、时段与优先级，
 * 并支持从产品定义读模型分页挑选 {@code wor_activity_definition} 活动。
 *
 * <p>初始化下拉依赖组织机构字典与术语集 {@code activity_category_code}；写操作操作人当前固定为
 * {@link #SYSTEM_OPERATOR_ID}，与无登录上下文的管理端约定一致。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecuteLocationAppService {

    /** 产品定义读模型中的活动定义表名，用于限定分页查询来源。 */
    private static final String TABLE_ACTIVITY_DEFINITION = "wor_activity_definition";
    /** 无登录上下文时的系统操作人占位主键。 */
    private static final Long SYSTEM_OPERATOR_ID = 0L;

    /** 执行规则请求/读模型与 DTO、领域命令映射。 */
    private final ExecuteLocationMapper executeLocationMapper;
    /** 执行规则读侧：按科室列出规则与详情投影。 */
    private final ActivityExecutionLocationQueryService activityExecutionLocationQueryService;
    /** 执行规则写侧：创建、更新与删除。 */
    private final ActivityExecutionLocationDomainService activityExecutionLocationDomainService;
    /** 组织机构读侧：初始化科室树下拉。 */
    private final OrganizationQueryService organizationQueryService;
    /** 术语读侧：诊疗大类等码表选项。 */
    private final CodeSystemQueryService codeSystemQueryService;
    /** 产品定义读模型分页：挑选待绑定的诊疗活动。 */
    private final ProductDefinitionCatalogPageLoader productDefinitionCatalogPageLoader;

    /**
     * 返回执行地点配置页初始化选项：执行科室树、诊疗大类；执行地点列表由页面二次加载故置空。
     *
     * @return 初始化 DTO
     */
    public ExecuteLocationInitDto init() {
        ExecuteLocationInitDto dto = new ExecuteLocationInitDto();
        dto.setExecutionOrgOptions(toOrganizationOptions(organizationQueryService.getOrganizationDictTree()));
        dto.setActivityCategoryOptions(
            codeSystemQueryService.getOptionListByCodeSystemName("activity_category_code"));
        dto.setExecutionLocationOptions(Collections.emptyList());
        return dto;
    }

    /**
     * 分页检索可供绑定的诊疗活动定义，结果以 {@link OptionDto} 形式供前端多选组件使用。
     *
     * @param request 分页条件，须含执行科室与诊疗大类
     * @return 分页选项；code 为产品定义主键字符串
     * @throws ValidationException 必填参数缺失时
     */
    public PagedResult<OptionDto> pageActivityDefinitionsForExecution(
        QueryActivityDefinitionForExecuteLocationRequest request
    ) {
        if (request == null || request.getExecutionOrgId() == null) {
            throw new ValidationException("执行科室ID不能为空");
        }
        if (request.getActivityCategoryCode() == null || request.getActivityCategoryCode().isBlank()) {
            throw new ValidationException("诊疗大类不能为空");
        }

        // 步骤一：组装产品定义分页查询（限定 wor_activity_definition 表）
        ProductDefinitionPageQuery query = new ProductDefinitionPageQuery();
        query.setProductTableName(TABLE_ACTIVITY_DEFINITION);
        query.setCategoryCode(request.getActivityCategoryCode());
        PageQueryBinder.bind(request, query);
        query.setPageSize(request.getBoundedPageSize());
        // 步骤二：读侧分页加载候选诊疗定义
        Page<ProductDefinitionDisplayDto> page = productDefinitionCatalogPageLoader.pageProductDefinition(query);
        // 步骤三：投影为前端多选 Option（code 为主键字符串）
        Page<OptionDto> optionPage = page.map(item -> new OptionDto(
            item.getProductDefinitionId(),
            resolveProductDefinitionIdValue(item),
            item.getProductName()
        ));
        return PagedResult.fromPage(optionPage);
    }

    /**
     * 按执行科室（及可选诊疗大类）列出已配置执行规则，并在应用层做内存分页截取。
     *
     * <p><b>规模说明</b>：当前实现先拉全量规则再在内存 {@code subList} 分页，规则数量显著增大时应改为
     * 查询服务层数据库分页以满足 B.2 下推要求。</p>
     *
     * @param request 查询请求，须含 {@code executionOrgId}
     * @return 内存分页后的 DTO 页
     * @throws ValidationException 执行科室 id 缺失时
     */
    public PagedResult<ExecuteLocationDto> listByExecutionOrgId(QueryExecuteLocationRequest request) {
        if (request == null || request.getExecutionOrgId() == null) {
            throw new ValidationException("执行科室ID不能为空");
        }
        List<ExecutionRuleListQueryModel> rows = activityExecutionLocationQueryService.listExecutionRulesByExecutionOrg(
            request.getExecutionOrgId(),
            request.getActivityCategoryCode()
        );
        List<ExecuteLocationDto> content = new ArrayList<>();
        for (ExecutionRuleListQueryModel row : rows) {
            content.add(executeLocationMapper.toDto(row));
        }

        int pageNum = 1;
        if (request.getPageNo() != null && request.getPageNo() > 0) {
            pageNum = request.getPageNo();
        }
        int pageSize = 20;
        if (request.getPageSize() != null && request.getPageSize() > 0) {
            pageSize = request.getPageSize();
        }
        // 在已加载的全量列表上计算当前页窗口（pageNum 为 1-based，与 AbstractPageQuery 一致）
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
     * @throws ValidationException 请求体为空时
     */
    public Long create(CreateExecuteLocationRequest request) {
        if (request == null) {
            throw new ValidationException("创建请求不能为空");
        }
        CreateExecutionRuleCommand command = new CreateExecutionRuleCommand();
        command.setExecutionOrgId(request.getExecutionOrgId());
        command.setActivityCategoryCode(request.getActivityCategoryCode());
        command.setOperatorId(SYSTEM_OPERATOR_ID);
        List<CreateExecutionRuleCommand.CreateExecutionRuleItemCommand> items = new ArrayList<>();
        items.add(executeLocationMapper.toCreateItem(request));
        command.setRules(items);
        // 批量下发领域创建，返回首条 id 供单条创建接口回显
        List<Long> ids = activityExecutionLocationDomainService.createExecutionRule(command);
        if (ids.isEmpty()) {
            return null;
        }
        return ids.get(0);
    }

    /**
     * 更新单条执行规则。
     *
     * @param request 更新请求
     * @throws ValidationException id 缺失时
     */
    public void update(UpdateExecuteLocationRequest request) {
        if (request == null || request.getId() == null) {
            throw new ValidationException("ID不能为空");
        }
        UpdateExecutionRuleCommand command = executeLocationMapper.toUpdateCommand(request);
        command.setOperatorId(SYSTEM_OPERATOR_ID);
        activityExecutionLocationDomainService.updateExecutionRule(command);
    }

    /**
     * 按字符串主键删除执行规则。
     *
     * @param request 含 {@link ExecuteLocationIdValueRequest#getValue()} 的删除请求
     * @throws ValidationException id 为空或格式非法时
     */
    public void deleteById(ExecuteLocationIdValueRequest request) {
        String idValue = null;
        if (request != null) {
            idValue = request.getValue();
        }
        Long id = TextValueConverter.requireLong(idValue, "ID不能为空", "ID格式不正确");
        DeleteExecutionRuleCommand command = new DeleteExecutionRuleCommand();
        command.setId(id);
        command.setOperatorId(SYSTEM_OPERATOR_ID);
        command.setDeleteReason("应用层删除执行规则");
        activityExecutionLocationDomainService.deleteExecutionRule(command);
    }

    /**
     * 批量创建执行规则：将多条诊疗定义展开为同一时段/地点的子命令后一次性调用领域批量创建。
     *
     * @param request 批量请求
     * @throws ValidationException 科室、大类或诊疗定义列表不合法时
     */
    public void batchCreate(BatchCreateExecuteLocationRequest request) {
        if (request == null || request.getExecutionOrgId() == null) {
            throw new ValidationException("执行科室ID不能为空");
        }
        if (request.getActivityCategoryCode() == null || request.getActivityCategoryCode().isBlank()) {
            throw new ValidationException("活动类别不能为空");
        }
        if (request.getActivityDefinitionIds() == null || request.getActivityDefinitionIds().isEmpty()) {
            throw new ValidationException("诊疗定义列表不能为空");
        }
        CreateExecutionRuleCommand command = new CreateExecutionRuleCommand();
        command.setExecutionOrgId(request.getExecutionOrgId());
        command.setActivityCategoryCode(request.getActivityCategoryCode());
        command.setOperatorId(SYSTEM_OPERATOR_ID);
        List<CreateExecutionRuleCommand.CreateExecutionRuleItemCommand> items = new ArrayList<>();
        // 每条诊疗定义生成一条子规则，共用批量请求中的科室、大类、时段与地点
        for (Long definitionId : request.getActivityDefinitionIds()) {
            CreateExecuteLocationRequest single = new CreateExecuteLocationRequest();
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
     * 将扁平组织机构列表组装为树形 {@link OptionDto}（children 递归），根节点为无父或在列表中找不到父的节点。
     *
     * @param organizations 组织机构字典树扁平结果
     * @return 树根选项列表
     */
    private List<OptionDto> toOrganizationOptions(List<Organization> organizations) {
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
        return buildOrganizationOptions(rootOrganizations, childrenByParentId);
    }

    /**
     * 递归构建机构树 Option：OptionDto 的 value 使用本地递增序号，code 为机构 id 字符串，label 为名称，busNo 携带业务号。
     *
     * @param organizations        当前层节点
     * @param childrenByParentId   父 id → 子列表索引
     * @return 当前层选项列表
     */
    private List<OptionDto> buildOrganizationOptions(
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
                children = buildOrganizationOptions(childOrganizations, childrenByParentId);
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
     * 产品定义选项的 code 字段：使用主键字符串，与前端选择回写 Long 一致。
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
