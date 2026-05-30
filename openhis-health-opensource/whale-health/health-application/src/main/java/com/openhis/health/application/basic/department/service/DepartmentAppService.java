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

package com.openhis.health.application.basic.department.service;

import com.openhis.health.application.basic.department.dto.DepartmentDetailDto;
import com.openhis.health.application.basic.department.dto.DepartmentDto;
import com.openhis.health.application.basic.department.dto.DepartmentInitDto;
import com.openhis.health.application.basic.department.mapper.DepartmentMapper;
import com.openhis.health.application.basic.department.request.CreateDepartmentRequest;
import com.openhis.health.application.basic.department.request.UpdateDepartmentRequest;
import com.openhis.health.domain.administration.organization.command.AbolishOrganizationCommand;
import com.openhis.health.domain.administration.organization.command.RelocateOrganizationCommand;
import com.openhis.health.domain.administration.organization.command.ReactivateOrganizationCommand;
import com.openhis.health.domain.administration.organization.command.RetireOrganizationCommand;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationDomainService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.share.enums.OrganizationClassification;
import com.openhis.health.domain.share.enums.OrganizationType;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import com.openhis.whale.identity.application.OrganizationUnitAppService;
import com.openhis.whale.identity.domain.OrganizationUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 科室应用服务：查询与树构建、初始化、增删改、启停，并与 identity 组织单元（OrganizationUnit）同步。
 *
 * <p>领域侧以 {@link Organization} 为真源；组织单元编码优先取 {@code busNo}，超长截断，缺省回退 {@code org-}{@code id}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentAppService {

    /** 写入 identity 组织单元 code 字段的最大长度。 */
    private static final int OU_CODE_MAX_LENGTH = 128;

    /** 组织读侧：科室树、字典树与按 id 展示加载。 */
    private final OrganizationQueryService organizationQueryService;
    /** 组织写侧：创建、修改、迁父、启停与作废。 */
    private final OrganizationDomainService organizationDomainService;
    /** 术语下拉：科室分类、类型等初始化选项。 */
    private final CodeSystemQueryService codeSystemQueryService;
    /** 科室请求/实体与 DTO、领域命令映射。 */
    private final DepartmentMapper departmentMapper;
    /** Identity 组织单元同步：与领域 Organization 双轨对齐。 */
    private final OrganizationUnitAppService organizationUnitAppService;
    /** 医务人员读侧：维护页默认医师下拉。 */
    private final PractitionerQueryService practitionerQueryService;

    /**
     * 全量科室树（启用机构平铺后组树）。
     *
     * @return 根节点列表
     */
    public List<DepartmentDto> getTree() {
        return toDtoList(organizationQueryService.getOrganizationTree());
    }

    /**
     * 责任科室场景下的组织树。
     *
     * @return 根节点列表
     */
    public List<DepartmentDto> getTreeForResponsibilityDept() {
        return toDtoList(organizationQueryService.getOrganizationTreeForResponsibilityDept());
    }

    /**
     * 药房场景下的组织树。
     *
     * @return 根节点列表
     */
    public List<DepartmentDto> getTreeForPharmacy() {
        return toDtoList(organizationQueryService.getOrganizationTreeForPharmacy());
    }

    /**
     * 仓储/库房场景下的组织树。
     *
     * @return 根节点列表
     */
    public List<DepartmentDto> getTreeForStorage() {
        return toDtoList(organizationQueryService.getOrganizationTreeForStorage());
    }

    /**
     * 按主键加载科室 DTO。
     *
     * @param id 组织主键
     * @return 科室节点
     */
    public DepartmentDto getDepartment(Long id) {
        validateId(id);
        Organization organization = organizationQueryService.loadOrganizationByIdForDisplay(id);
        if (organization == null) {
            throw new ValidationException("科室不存在");
        }
        return departmentMapper.toDto(organization);
    }

    /**
     * 与 {@link #getDepartment(Long)} 等价，供部分控制器路径别名调用。
     *
     * @param id 组织主键
     * @return 科室节点
     */
    public DepartmentDto getDepartmentById(Long id) {
        return getDepartment(id);
    }

    /**
     * 科室维护页初始化：分类、类型、上级树、默认医师下拉。
     *
     * @return 初始化 DTO
     */
    public DepartmentInitDto init() {
        DepartmentInitDto initDto = new DepartmentInitDto();
        initDto.setDepartmentClassOptions(codeSystemQueryService.getOptionListByEnum(OrganizationClassification.class));
        initDto.setDepartmentTypeOptions(codeSystemQueryService.getOptionListByEnum(OrganizationType.class));
        initDto.setDepartmentOptions(this.loadDepartmentOptions());
        initDto.setPractitionerOptions(this.loadPractitionerOptions());
        return initDto;
    }

    /**
     * 科室详情：含上级科室嵌套对象（若有）。
     *
     * @param deptId 科室主键
     * @return 详情 DTO
     */
    public DepartmentDetailDto getDepartmentDetail(Long deptId) {
        validateId(deptId);
        Organization organization = organizationQueryService.loadOrganizationByIdForDisplay(deptId);
        if (organization == null) {
            throw new ValidationException("科室不存在");
        }
        DepartmentDetailDto detailDto = departmentMapper.toDetailDto(organization);
        if (organization.getParentId() != null) {
            Organization parent = organizationQueryService.loadOrganizationByIdForDisplay(organization.getParentId());
            detailDto.setParentDepartment(departmentMapper.toDetailDto(parent));
        }
        return detailDto;
    }

    /**
     * 科室列表（当前等同全树）。
     *
     * @return 根节点列表
     */
    public List<DepartmentDto> getDepartmentList() {
        return getTree();
    }

    /**
     * 新建科室并同步创建/更新 identity 组织单元。
     *
     * @param request 创建请求
     * @return 新建后的科室 DTO
     */
    public DepartmentDto createDepartment(CreateDepartmentRequest request) {
        if (request == null) {
            throw new ValidationException("创建科室失败：请求不能为空");
        }
        // 步骤一：领域落库组织主档（管理端无登录上下文，操作人占位 0）
        Long createdId = organizationDomainService.createOrganization(departmentMapper.toCreateCommand(request, 0L));
        Organization organization = organizationQueryService.loadOrganizationByIdForDisplay(createdId);
        // 步骤二：同步 identity 侧 OrganizationUnit，避免权限树与 HIS 科室脱节
        syncCreateOu(organization);
        return getDepartment(createdId);
    }

    /**
     * 编辑科室：可选先迁父节点，再改组织属性并同步组织单元。
     *
     * @param request 编辑请求
     * @return 更新后 DTO
     */
    public DepartmentDto editDepartment(UpdateDepartmentRequest request) {
        if (request == null || request.getId() == null) {
            throw new ValidationException("编辑科室失败：科室ID不能为空");
        }
        Organization current = organizationQueryService.loadOrganizationByIdForDisplay(request.getId());
        if (current == null) {
            throw new ValidationException("科室不存在");
        }
        // 仅在前端显式声明“上级已变更”时才执行 relocate；
        // 支持 newParentId=null 表示“提升为根节点”。
        if (Boolean.TRUE.equals(request.getParentIdChanged())
            && !isSameParent(current.getParentId(), request.getParentId())) {
            relocateDepartment(request.getId(), request.getParentId());
        }
        // 步骤二：更新组织属性（名称、分类、医保信息等）
        organizationDomainService.modifyOrganization(departmentMapper.toModifyCommand(request, 0L));
        Organization organization = organizationQueryService.loadOrganizationByIdForDisplay(request.getId());
        // 步骤三：回写 identity 组织单元展示名、排序与父子关系
        syncUpdateOu(organization);
        return getDepartment(request.getId());
    }

    /**
     * 作废科室并软删对应组织单元。
     *
     * @param id 科室主键
     */
    public void deleteDepartment(Long id) {
        validateId(id);
        Organization organization = organizationQueryService.loadOrganizationByIdForDisplay(id);
        if (organization == null) {
            throw new ValidationException("科室不存在");
        }
        AbolishOrganizationCommand command = new AbolishOrganizationCommand();
        command.setOperatorId(0L);
        AbolishOrganizationCommand.AbolishOrganizationItemCommand item =
            new AbolishOrganizationCommand.AbolishOrganizationItemCommand();
        item.setOrganizationId(id);
        item.setAbolishReason("应用层删除科室");
        command.setItem(item);
        organizationDomainService.abolishOrganization(command);
        syncDeleteOu(organization);
    }

    /**
     * 批量启用科室及组织单元。
     *
     * @param deptIds 主键列表
     * @return 是否执行过领域调用（无有效 id 时为 false）
     */
    public boolean activeDepartment(List<Long> deptIds) {
        List<Long> validDeptIds = collectValidDeptIds(deptIds);
        if (validDeptIds.isEmpty()) {
            return false;
        }
        ReactivateOrganizationCommand command = new ReactivateOrganizationCommand();
        command.setOperatorId(0L);
        List<ReactivateOrganizationCommand.ReactivateOrganizationItemCommand> items = new ArrayList<>();
        for (Long deptId : validDeptIds) {
            // 逐条构建启用命令项，保持领域命令语义清晰。
            ReactivateOrganizationCommand.ReactivateOrganizationItemCommand item =
                new ReactivateOrganizationCommand.ReactivateOrganizationItemCommand();
            item.setOrganizationId(deptId);
            items.add(item);
        }
        command.setItems(items);
        organizationDomainService.reactivateOrganization(command);
        // 启用领域组织后，同步启用 identity 侧组织单元。
        syncOrganizationOuEnabled(validDeptIds, true);
        return true;
    }

    /**
     * 批量停用科室及组织单元。
     *
     * @param deptIds 主键列表
     * @return 是否执行过领域调用
     */
    public boolean inactiveDepartment(List<Long> deptIds) {
        List<Long> validDeptIds = collectValidDeptIds(deptIds);
        if (validDeptIds.isEmpty()) {
            return false;
        }
        RetireOrganizationCommand command = new RetireOrganizationCommand();
        command.setOperatorId(0L);
        List<RetireOrganizationCommand.RetireOrganizationItemCommand> items = new ArrayList<>();
        for (Long deptId : validDeptIds) {
            // 逐条构建停用命令项，保持领域命令语义清晰。
            RetireOrganizationCommand.RetireOrganizationItemCommand item =
                new RetireOrganizationCommand.RetireOrganizationItemCommand();
            item.setOrganizationId(deptId);
            items.add(item);
        }
        command.setItems(items);
        organizationDomainService.retireOrganization(command);
        // 停用领域组织后，同步停用 identity 侧组织单元。
        syncOrganizationOuEnabled(validDeptIds, false);
        return true;
    }

    /**
     * 将平铺 Organization 列表转换为树形 DepartmentDto 列表。
     *
     * <p>说明：查询服务返回的是启用机构平铺列表；此处按 parentId 建树，
     * 使前端可以直接基于 children 渲染树表，而无需再次组树。</p>
     *
     * @param organizations 机构平铺列表
     * @return 根节点列表
     */
    private List<DepartmentDto> toDtoList(List<Organization> organizations) {
        if (organizations == null || organizations.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, DepartmentDto> nodeById = new LinkedHashMap<>();
        List<DepartmentDto> orderedNodes = new ArrayList<>();
        for (Organization organization : organizations) {
            if (organization == null) {
                continue;
            }
            DepartmentDto dto = departmentMapper.toDto(organization);
            if (dto == null) {
                continue;
            }
            orderedNodes.add(dto);
            if (dto.getId() != null) {
                nodeById.put(dto.getId(), dto);
            }
        }

        List<DepartmentDto> roots = new ArrayList<>();
        for (DepartmentDto node : orderedNodes) {
            Long parentId = node.getParentId();
            DepartmentDto parent = null;
            if (parentId != null) {
                parent = nodeById.get(parentId);
            }
            if (parent == null) {
                roots.add(node);
                continue;
            }
            if (parent.getChildren() == null) {
                parent.setChildren(new ArrayList<>());
            }
            parent.getChildren().add(node);
        }
        return roots;
    }

    /**
     * 过滤并收敛有效科室 ID。
     *
     * @param deptIds 原始 ID 列表
     * @return 去除 null 后的可用 ID 列表
     */
    private List<Long> collectValidDeptIds(List<Long> deptIds) {
        List<Long> validDeptIds = new ArrayList<>();
        if (deptIds == null || deptIds.isEmpty()) {
            return validDeptIds;
        }
        for (Long deptId : deptIds) {
            if (deptId == null) {
                continue;
            }
            validDeptIds.add(deptId);
        }
        return validDeptIds;
    }

    /**
     * 主键非空校验。
     *
     * @param id 科室 id
     */
    private void validateId(Long id) {
        if (id == null) {
            throw new ValidationException("科室ID不能为空");
        }
    }

    /**
     * 领域组织新建后：按 id 或 code 对齐插入或更新 {@link OrganizationUnit}。
     *
     * @param organization 已持久化组织
     */
    private void syncCreateOu(Organization organization) {
        if (organization == null) {
            return;
        }
        String code = buildOuCode(organization);
        Optional<OrganizationUnit> existingById = organizationUnitAppService.findOptionalById(organization.getId());
        Optional<OrganizationUnit> existingByCode = organizationUnitAppService.findOptionalByCode(code);
        assertNoOuIdCodeDrift(organization, code, existingById, existingByCode);
        if (existingById.isPresent()) {
            OrganizationUnit ou = existingById.get();
            ou.setDisplayName(resolveOuDisplayName(organization));
            ou.setSortOrder(resolveSortOrder(organization.getDisplayOrder()));
            ou.setIsEnabled(Boolean.TRUE.equals(organization.getActiveFlag()));
            ou.setParentId(organization.getParentId());
            organizationUnitAppService.persistOrganizationUnit(ou);
            return;
        }
        OrganizationUnit ou = new OrganizationUnit();
        ou.setId(organization.getId());
        ou.setCode(code);
        ou.setDisplayName(resolveOuDisplayName(organization));
        ou.setSortOrder(resolveSortOrder(organization.getDisplayOrder()));
        ou.setIsEnabled(Boolean.TRUE.equals(organization.getActiveFlag()));
        ou.setParentId(organization.getParentId());
        ou.setTenantId(TenantUtils.getCurrentTenantId());
        organizationUnitAppService.persistOrganizationUnit(ou);
    }

    /**
     * 组织变更后同步更新组织单元展示名、排序、启用与父子关系。
     *
     * @param organization 最新组织快照
     */
    private void syncUpdateOu(Organization organization) {
        if (organization == null) {
            return;
        }
        String code = buildOuCode(organization);
        Optional<OrganizationUnit> existingById = organizationUnitAppService.findOptionalById(organization.getId());
        Optional<OrganizationUnit> existingByCode = organizationUnitAppService.findOptionalByCode(code);
        assertNoOuIdCodeDrift(organization, code, existingById, existingByCode);
        if (existingById.isEmpty()) {
            syncCreateOu(organization);
            return;
        }
        OrganizationUnit ou = existingById.get();
        ou.setCode(code);
        ou.setDisplayName(resolveOuDisplayName(organization));
        ou.setSortOrder(resolveSortOrder(organization.getDisplayOrder()));
        ou.setIsEnabled(Boolean.TRUE.equals(organization.getActiveFlag()));
        ou.setParentId(organization.getParentId());
        organizationUnitAppService.persistOrganizationUnit(ou);
    }

    /**
     * 领域作废后标记组织单元逻辑删除。
     *
     * @param organization 被作废组织
     */
    private void syncDeleteOu(Organization organization) {
        if (organization == null) {
            return;
        }
        String code = buildOuCode(organization);
        Optional<OrganizationUnit> existingById = organizationUnitAppService.findOptionalById(organization.getId());
        Optional<OrganizationUnit> existingByCode = organizationUnitAppService.findOptionalByCode(code);
        assertNoOuIdCodeDrift(organization, code, existingById, existingByCode);
        if (existingById.isEmpty()) {
            return;
        }
        OrganizationUnit ou = existingById.get();
        ou.setIsDeleted(Boolean.TRUE);
        ou.setDeletedAt(LocalDateTime.now());
        organizationUnitAppService.persistOrganizationUnit(ou);
    }

    /**
     * 按科室 id 列表批量切换组织单元启用状态。
     *
     * @param deptIds 科室主键
     * @param enabled   目标启用状态
     */
    private void syncOrganizationOuEnabled(List<Long> deptIds, boolean enabled) {
        for (Long deptId : deptIds) {
            if (deptId == null) {
                continue;
            }
            Organization organization = organizationQueryService.loadOrganizationByIdForDisplay(deptId);
            if (organization == null) {
                continue;
            }
            String code = buildOuCode(organization);
            Optional<OrganizationUnit> existingById = organizationUnitAppService.findOptionalById(organization.getId());
            Optional<OrganizationUnit> existingByCode = organizationUnitAppService.findOptionalByCode(code);
            assertNoOuIdCodeDrift(organization, code, existingById, existingByCode);
            if (existingById.isEmpty()) {
                continue;
            }
            OrganizationUnit ou = existingById.get();
            ou.setIsEnabled(enabled);
            organizationUnitAppService.persistOrganizationUnit(ou);
        }
    }

    /**
     * 校验组织 id 与组织单元 code 不出现交叉占用（防双轨漂移）。
     *
     * @param organization   当前组织
     * @param code           推导出的 OU code
     * @param existingById   同 id 的 OU
     * @param existingByCode 同 code 的 OU
     */
    private void assertNoOuIdCodeDrift(
        Organization organization,
        String code,
        Optional<OrganizationUnit> existingById,
        Optional<OrganizationUnit> existingByCode
    ) {
        if (organization == null || organization.getId() == null || code == null || code.isBlank()) {
            return;
        }
        if (existingByCode.isPresent() && !organization.getId().equals(existingByCode.get().getId())) {
            throw new ValidationException("组织机构与组织单元ID不一致：busNo/code=" + code
                + "，organizationId=" + organization.getId()
                + "，organizationUnitId=" + existingByCode.get().getId());
        }
        if (existingById.isPresent() && !code.equals(existingById.get().getCode())) {
            throw new ValidationException("组织机构与组织单元编码不一致：organizationId=" + organization.getId()
                + "，organizationBusNo=" + code
                + "，organizationUnitCode=" + existingById.get().getCode());
        }
    }

    /**
     * 生成组织单元 code：优先 busNo，空则用 org-主键，超长截断至 {@link #OU_CODE_MAX_LENGTH}。
     *
     * @param organization 组织
     * @return code 字符串，可能为 null
     */
    private String buildOuCode(Organization organization) {
        if (organization == null) {
            return null;
        }
        if (organization.getBusNo() == null || organization.getBusNo().isBlank()) {
            if (organization.getId() == null) {
                return null;
            }
            return "org-" + organization.getId();
        }
        if (organization.getBusNo().length() <= OU_CODE_MAX_LENGTH) {
            return organization.getBusNo();
        }
        return organization.getBusNo().substring(0, OU_CODE_MAX_LENGTH);
    }

    /**
     * 组织单元展示名：缺省为占位串 {@code department}。
     *
     * @param organization 组织
     * @return 展示名
     */
    private String resolveOuDisplayName(Organization organization) {
        if (organization == null || organization.getName() == null || organization.getName().isBlank()) {
            return "department";
        }
        return organization.getName();
    }

    /**
     * 排序号归一：null 视为 0。
     *
     * @param displayOrder 科室显示顺序
     * @return OU sortOrder
     */
    private Integer resolveSortOrder(Integer displayOrder) {
        if (displayOrder == null) {
            return 0;
        }
        return displayOrder;
    }

    /**
     * 加载门诊挂号可选医师下拉。
     *
     * @return 选项列表
     */
    private List<OptionDto> loadPractitionerOptions() {
        List<Practitioner> practitioners = practitionerQueryService.listPractitionersForOutpatientRegistration(null);
        List<OptionDto> options = new ArrayList<>();
        long sort = 0L;
        for (Practitioner practitioner : practitioners) {
            if (practitioner == null || practitioner.getId() == null) {
                continue;
            }
            sort++;
            options.add(new OptionDto(sort, String.valueOf(practitioner.getId()), practitioner.getName()));
        }
        return options;
    }

    /**
     * 调用领域迁父命令。
     *
     * @param organizationId 待调整组织
     * @param newParentId    新父 id，可为 null 表示提升为根
     */
    private void relocateDepartment(Long organizationId, Long newParentId) {
        RelocateOrganizationCommand command = new RelocateOrganizationCommand();
        command.setOperatorId(0L);
        RelocateOrganizationCommand.RelocateOrganizationItemCommand item =
            new RelocateOrganizationCommand.RelocateOrganizationItemCommand();
        item.setOrganizationId(organizationId);
        item.setNewParentId(newParentId);
        command.setItem(item);
        organizationDomainService.relocateOrganization(command);
    }

    /**
     * 判断两父 id 是否同指（均 null 视为相同）。
     *
     * @param leftParentId  当前父
     * @param rightParentId 请求父
     * @return 是否一致
     */
    private boolean isSameParent(Long leftParentId, Long rightParentId) {
        if (leftParentId == null && rightParentId == null) {
            return true;
        }
        if (leftParentId == null || rightParentId == null) {
            return false;
        }
        return leftParentId.equals(rightParentId);
    }

    /**
     * 上级科室树形下拉数据。
     *
     * @return 树根选项
     */
    private List<OptionDto> loadDepartmentOptions() {
        List<Organization> organizations = organizationQueryService.getOrganizationDictTree();
        return toOrganizationOptions(organizations);
    }

    /**
     * 组织机构扁平列表转树形 {@link OptionDto}。
     *
     * @param organizations 字典树扁平结果
     * @return 树根选项
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
     * 递归构建机构树选项。
     *
     * @param organizations        当前层
     * @param childrenByParentId   子索引
     * @return 当前层选项
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
}
