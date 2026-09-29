/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.department.service;

import com.openhis.health.application.basic.department.dto.DepartmentDetailDto;
import com.openhis.health.application.basic.department.dto.DepartmentDto;
import com.openhis.health.application.basic.department.dto.DepartmentInitDto;
import com.openhis.health.application.basic.department.mapper.DepartmentMapper;
import com.openhis.health.application.basic.department.request.CreateDepartmentRequest;
import com.openhis.health.application.basic.department.request.QueryDepartmentListRequest;
import com.openhis.health.application.basic.department.request.UpdateDepartmentRequest;
import com.openhis.health.application.shared.practitioner.PractitionerRoleScopeLoader;
import com.openhis.health.infrastructure.platform.organization.OrganizationUnitLinkCode;
import com.openhis.health.domain.administration.organization.command.AbolishOrganizationCommand;
import com.openhis.health.domain.administration.organization.command.RelocateOrganizationCommand;
import com.openhis.health.domain.administration.organization.command.ReactivateOrganizationCommand;
import com.openhis.health.domain.administration.organization.command.RetireOrganizationCommand;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.query.OrganizationManagementListQuery;
import com.openhis.health.domain.administration.organization.service.OrganizationDomainService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
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
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
 * <p>领域侧以 {@link Organization} 为真源；与 identity {@link OrganizationUnit} 通过
 * {@link OrganizationUnitLinkCode} 关联（{@code code} = busNo 或 {@code org-}{@code id}），两侧主键独立。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentAppService {

    /** 组织读侧：科室树、字典树与按 id 展示加载。 */
    private final OrganizationQueryService organizationQueryService;
    /** 组织写侧：创建、修改、迁父、启停与作废。 */
    private final OrganizationDomainService organizationDomainService;
    /** 写路径操作人门控（B.6.5）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;
    /** 术语下拉：科室分类、类型等初始化选项。 */
    private final CodeSystemQueryService codeSystemQueryService;
    /** 科室请求/实体与 DTO、领域命令映射。 */
    private final DepartmentMapper departmentMapper;
    /** Identity 组织单元同步：与领域 Organization 双轨对齐。 */
    private final OrganizationUnitAppService organizationUnitAppService;
    /** 医务人员读侧：维护页默认医师下拉。 */
    private final PractitionerQueryService practitionerQueryService;
    /** 岗位 → 医务人员主键预解析（B.6.1.3.1）。 */
    private final PractitionerRoleScopeLoader practitionerRoleScopeLoader;

    /**
     * 全量科室树（启用机构平铺后组树）。
     *
     * @return 根节点列表
     */
    public List<DepartmentDto> getTree() {
        // Gate：只读树查询，无写侧操作人
        // Pre：读侧加载启用机构树
        // Asm：平铺列表组树并映射 DTO
        return toDtoList(organizationQueryService.getOrganizationTree());
    }

    /**
     * 责任科室场景下的组织树。
     *
     * @return 根节点列表
     */
    public List<DepartmentDto> getTreeForResponsibilityDept() {
        // Gate：只读责任科室树，无写侧操作人
        // Pre：读侧加载责任科室场景组织树
        // Asm：平铺列表组树并映射 DTO
        return toDtoList(organizationQueryService.getOrganizationTreeForResponsibilityDept());
    }

    /**
     * 按主键加载科室 DTO。
     *
     * @param id 组织主键
     * @return 科室节点
     */
    public DepartmentDto getDepartment(Long id) {
        // Gate：主键非空
        validateId(id);
        // Pre：按 id 加载组织展示快照
        Organization organization = organizationQueryService.loadOrganizationByIdForDisplay(id);
        if (organization == null) {
            throw new ValidationException("科室不存在");
        }
        // Asm：映射科室 DTO
        return departmentMapper.toDto(organization);
    }

    /**
     * 与 {@link #getDepartment(Long)} 等价，供部分控制器路径别名调用。
     *
     * @param id 组织主键
     * @return 科室节点
     */
    public DepartmentDto getDepartmentById(Long id) {
        // Gate/Pre/Asm：与 getDepartment 等价，供控制器路径别名
        return getDepartment(id);
    }

    /**
     * 科室维护页初始化：分类、类型、上级树、默认医师下拉。
     *
     * @return 初始化 DTO
     */
    public DepartmentInitDto init() {
        // Gate：只读页初始化，无写侧操作人
        DepartmentInitDto initDto = new DepartmentInitDto();
        // Pre：加载分类、类型、上级树与默认医师下拉
        initDto.setDepartmentClassOptions(codeSystemQueryService.getOptionListByEnum(OrganizationClassification.class));
        initDto.setDepartmentTypeOptions(codeSystemQueryService.getOptionListByEnum(OrganizationType.class));
        initDto.setDepartmentOptions(this.loadDepartmentOptions());
        initDto.setPractitionerOptions(this.loadPractitionerOptions());
        // Asm：映射维护页初始化 DTO
        return initDto;
    }

    /**
     * 科室详情：含上级科室嵌套对象（若有）。
     *
     * @param deptId 科室主键
     * @return 详情 DTO
     */
    public DepartmentDetailDto getDepartmentDetail(Long deptId) {
        // Gate：主键非空
        validateId(deptId);
        // Pre：加载科室及上级科室快照
        Organization organization = organizationQueryService.loadOrganizationByIdForDisplay(deptId);
        if (organization == null) {
            throw new ValidationException("科室不存在");
        }
        DepartmentDetailDto detailDto = departmentMapper.toDetailDto(organization);
        // 业务分支：存在上级时嵌套父科室对象
        if (organization.getParentId() != null) {
            Organization parent = organizationQueryService.loadOrganizationByIdForDisplay(organization.getParentId());
            detailDto.setParentDepartment(departmentMapper.toDetailDto(parent));
        }
        // Asm：映射详情 DTO
        return detailDto;
    }

    /**
     * 科室列表（管理页：含 ACTIVE 与 INACTIVE 停用科室，不含 DISCARDED 已删除）。
     *
     * @param request 查询条件；字段均可空
     * @return 根节点列表
     */
    public List<DepartmentDto> getDepartmentList(QueryDepartmentListRequest request) {
        // Gate：只读列表，筛选字段均可空
        // Pre：请求转领域管理列表 Query 并查询
        OrganizationManagementListQuery query = toManagementListQuery(request);
        // Asm：平铺列表组树并映射 DTO
        return toDtoList(organizationQueryService.listOrganizationsForManagement(query));
    }

    /**
     * 新建科室并同步创建/更新 identity 组织单元。
     *
     * @param request 创建请求
     * @return 新建后的科室 DTO
     */
    public DepartmentDto createDepartment(CreateDepartmentRequest request) {
        // Gate：请求体非空与组织层级合法
        if (request == null) {
            throw new ValidationException("创建科室失败：请求不能为空");
        }
        validateOrganizationHierarchy(request.getTypeEnum(), request.getParentId(), null);
        // Gate：写路径操作人（B.6.5）
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.ORGANIZATION_INVALID_ARGUMENT, "请先登录后再新建科室");
        // Cmd：领域创建组织主档
        Long createdId = organizationDomainService.createOrganization(
            departmentMapper.toCreateCommand(request, operatorId));
        // Pre：回读新建组织快照
        Organization organization = organizationQueryService.loadOrganizationByIdForDisplay(createdId);
        // Cmd 续：同步 identity 侧 OrganizationUnit，避免权限树与 HIS 科室脱节
        syncCreateOu(organization);
        // Asm：映射新建后科室 DTO
        return getDepartment(createdId);
    }

    /**
     * 编辑科室：可选先迁父节点，再改组织属性并同步组织单元。
     *
     * @param request 编辑请求
     * @return 更新后 DTO
     */
    public DepartmentDto editDepartment(UpdateDepartmentRequest request) {
        // Gate：请求体与主键非空
        if (request == null || request.getId() == null) {
            throw new ValidationException("编辑科室失败：科室ID不能为空");
        }
        // Pre：加载当前组织快照并校验层级
        Organization current = organizationQueryService.loadOrganizationByIdForDisplay(request.getId());
        if (current == null) {
            throw new ValidationException("科室不存在");
        }
        validateOrganizationHierarchy(request.getTypeEnum(), request.getParentId(), current);
        // Gate：写路径操作人
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.ORGANIZATION_INVALID_ARGUMENT, "请先登录后再编辑科室");
        // 业务分支：前端显式声明上级变更时才 relocate（含提升为根）
        if (Boolean.TRUE.equals(request.getParentIdChanged())
            && !isSameParent(current.getParentId(), request.getParentId())) {
            relocateDepartment(request.getId(), request.getParentId(), operatorId);
        }
        // Cmd：更新组织属性
        organizationDomainService.modifyOrganization(
            departmentMapper.toModifyCommand(request, operatorId));
        // Pre：回读变更后组织快照
        Organization organization = organizationQueryService.loadOrganizationByIdForDisplay(request.getId());
        // Cmd 续：按 code 回写 identity 组织单元
        syncUpdateOu(organization, current);
        // Asm：映射更新后 DTO
        return getDepartment(request.getId());
    }

    /**
     * 作废科室并软删对应组织单元。
     *
     * @param id 科室主键
     */
    public void deleteDepartment(Long id) {
        // Gate：主键非空且科室存在
        validateId(id);
        Organization organization = organizationQueryService.loadOrganizationByIdForDisplay(id);
        if (organization == null) {
            throw new ValidationException("科室不存在");
        }
        // Gate：写路径操作人
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.ORGANIZATION_INVALID_ARGUMENT, "请先登录后再删除科室");
        // Pre：组装作废命令
        AbolishOrganizationCommand command = new AbolishOrganizationCommand();
        command.setOperatorId(operatorId);
        AbolishOrganizationCommand.AbolishOrganizationItemCommand item =
            new AbolishOrganizationCommand.AbolishOrganizationItemCommand();
        item.setOrganizationId(id);
        item.setAbolishReason("应用层删除科室");
        command.setItem(item);
        // Cmd：领域作废组织
        organizationDomainService.abolishOrganization(command);
        // Cmd 续：同步软删 identity 组织单元
        syncDeleteOu(organization);
    }

    /**
     * 批量启用科室及组织单元。
     *
     * @param deptIds 主键列表
     * @return 是否执行过领域调用（无有效 id 时为 false）
     */
    public boolean activeDepartment(List<Long> deptIds) {
        // Gate：有效主键列表非空
        List<Long> validDeptIds = collectValidDeptIds(deptIds);
        if (validDeptIds.isEmpty()) {
            return false;
        }
        // Gate：写路径操作人
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.ORGANIZATION_INVALID_ARGUMENT, "请先登录后再启用科室");
        // Pre：组装批量启用命令
        ReactivateOrganizationCommand command = new ReactivateOrganizationCommand();
        command.setOperatorId(operatorId);
        List<ReactivateOrganizationCommand.ReactivateOrganizationItemCommand> items = new ArrayList<>();
        for (Long deptId : validDeptIds) {
            // 逐条构建启用命令项，保持领域命令语义清晰。
            ReactivateOrganizationCommand.ReactivateOrganizationItemCommand item =
                new ReactivateOrganizationCommand.ReactivateOrganizationItemCommand();
            item.setOrganizationId(deptId);
            items.add(item);
        }
        command.setItems(items);
        // Cmd：领域批量启用
        organizationDomainService.reactivateOrganization(command);
        // Cmd 续：同步启用 identity 组织单元
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
        // Gate：有效主键列表非空
        List<Long> validDeptIds = collectValidDeptIds(deptIds);
        if (validDeptIds.isEmpty()) {
            return false;
        }
        // Gate：写路径操作人
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.ORGANIZATION_INVALID_ARGUMENT, "请先登录后再停用科室");
        // Pre：组装批量停用命令
        RetireOrganizationCommand command = new RetireOrganizationCommand();
        command.setOperatorId(operatorId);
        List<RetireOrganizationCommand.RetireOrganizationItemCommand> items = new ArrayList<>();
        for (Long deptId : validDeptIds) {
            // 逐条构建停用命令项，保持领域命令语义清晰。
            RetireOrganizationCommand.RetireOrganizationItemCommand item =
                new RetireOrganizationCommand.RetireOrganizationItemCommand();
            item.setOrganizationId(deptId);
            items.add(item);
        }
        command.setItems(items);
        // Cmd：领域批量停用
        organizationDomainService.retireOrganization(command);
        // Cmd 续：同步停用 identity 组织单元
        syncOrganizationOuEnabled(validDeptIds, false);
        return true;
    }

    /**
     * 将应用层列表查询请求转为领域查询对象。
     *
     * @param request HTTP 请求体；{@code null} 时返回空条件
     * @return 领域查询对象
     */
    private OrganizationManagementListQuery toManagementListQuery(QueryDepartmentListRequest request) {
        OrganizationManagementListQuery query = new OrganizationManagementListQuery();
        if (request == null) {
            return query;
        }
        if (StringUtils.isNotEmpty(request.getKeyword())) {
            query.setKeyword(request.getKeyword().trim());
        }
        if (StringUtils.isNotEmpty(request.getTypeEnum())) {
            query.setTypeEnum(request.getTypeEnum().trim());
        }
        if (StringUtils.isNotEmpty(request.getClassEnum())) {
            query.setClassEnum(request.getClassEnum().trim());
        }
        if (StringUtils.isNotEmpty(request.getStatusEnum())) {
            query.setStatusEnum(request.getStatusEnum().trim());
        }
        return query;
    }

    /**
     * 将平铺 Organization 列表转换为树形 DepartmentDto 列表。
     *
     * <p>说明：查询服务返回管理页平铺列表（含 INACTIVE 停用）；此处按 parentId 建树，
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
        this.sortDepartmentDtoTreeByDisplayOrder(roots);
        return roots;
    }

    /**
     * 递归按 displayOrder、id 升序排列科室树同级节点（null 排序号置后）。
     *
     * @param nodes 当前层节点
     */
    private void sortDepartmentDtoTreeByDisplayOrder(List<DepartmentDto> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        nodes.sort(Comparator
            .comparing(DepartmentDto::getDisplayOrder, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(DepartmentDto::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        for (DepartmentDto node : nodes) {
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                this.sortDepartmentDtoTreeByDisplayOrder(node.getChildren());
            }
        }
    }

    /**
     * 同级机构按 displayOrder、id 升序排序（null 排序号置后）。
     *
     * @param organizations 同级机构列表
     * @return 排序副本
     */
    private List<Organization> sortOrganizationsByDisplayOrder(List<Organization> organizations) {
        if (organizations == null || organizations.isEmpty()) {
            return Collections.emptyList();
        }
        List<Organization> sorted = new ArrayList<>(organizations);
        sorted.sort(Comparator
            .comparing(Organization::getDisplayOrder, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Organization::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        return sorted;
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
     * 领域组织新建后：按 {@link OrganizationUnitLinkCode} 同步 {@link OrganizationUnit}。
     *
     * <p>须含软删行查找：{@code whale_organization_units.code} 唯一约束含已删行；
     * 若仅 {@code findOptionalByCode}（软删过滤）会漏掉已删 OU，随后 INSERT 同 code 触发
     * {@code whale_organization_units_code_key}。与 {@code OrganizationUnitLinkSyncService} 同口径。</p>
     */
    private void syncCreateOu(Organization organization) {
        if (organization == null) {
            return;
        }
        String code = OrganizationUnitLinkCode.fromOrganization(organization);
        if (code == null || code.isBlank()) {
            return;
        }
        // Pre：含软删按 code 找 OU，避免唯一键冲突后重插
        Optional<OrganizationUnit> existingByCode =
            organizationUnitAppService.findOptionalByCodeIncludingDeleted(code);
        if (existingByCode.isPresent()) {
            OrganizationUnit ou = existingByCode.get();
            applyOrganizationToOu(organization, ou);
            restoreOuIfOrganizationActive(organization, ou);
            organizationUnitAppService.persistOrganizationUnit(ou);
            return;
        }
        OrganizationUnit ou = new OrganizationUnit();
        ou.setCode(code);
        applyOrganizationToOu(organization, ou);
        ou.setTenantId(TenantUtils.getCurrentTenantId());
        organizationUnitAppService.persistOrganizationUnit(ou);
    }

    /**
     * 组织变更后同步更新组织单元；若 busNo 变更则沿旧 code 找到 OU 并迁移关联键。
     *
     * @param organization       变更后组织快照
     * @param previousSnapshot   变更前组织快照（用于 code 迁移；可为 null）
     */
    private void syncUpdateOu(Organization organization, Organization previousSnapshot) {
        if (organization == null) {
            return;
        }
        String newCode = OrganizationUnitLinkCode.fromOrganization(organization);
        if (newCode == null || newCode.isBlank()) {
            return;
        }
        // Pre：含软删按新 code 找 OU（同 syncCreateOu，防唯一键重插）
        Optional<OrganizationUnit> existingByNewCode =
            organizationUnitAppService.findOptionalByCodeIncludingDeleted(newCode);
        if (existingByNewCode.isPresent()) {
            assertOuCodeOwnedByOrganization(existingByNewCode.get(), organization.getId(), newCode);
            OrganizationUnit ou = existingByNewCode.get();
            applyOrganizationToOu(organization, ou);
            restoreOuIfOrganizationActive(organization, ou);
            organizationUnitAppService.persistOrganizationUnit(ou);
            return;
        }
        if (previousSnapshot != null) {
            String oldCode = OrganizationUnitLinkCode.fromOrganization(previousSnapshot);
            if (oldCode != null && !oldCode.isBlank() && !oldCode.equals(newCode)) {
                Optional<OrganizationUnit> existingByOldCode =
                    organizationUnitAppService.findOptionalByCodeIncludingDeleted(oldCode);
                if (existingByOldCode.isPresent()) {
                    OrganizationUnit ou = existingByOldCode.get();
                    ou.setCode(newCode);
                    applyOrganizationToOu(organization, ou);
                    restoreOuIfOrganizationActive(organization, ou);
                    organizationUnitAppService.persistOrganizationUnit(ou);
                    return;
                }
            }
        }
        syncCreateOu(organization);
    }

    /**
     * 领域作废后标记组织单元逻辑删除。
     */
    private void syncDeleteOu(Organization organization) {
        if (organization == null) {
            return;
        }
        String code = OrganizationUnitLinkCode.fromOrganization(organization);
        if (code == null || code.isBlank()) {
            return;
        }
        Optional<OrganizationUnit> existingByCode = organizationUnitAppService.findOptionalByCode(code);
        if (existingByCode.isEmpty()) {
            return;
        }
        OrganizationUnit ou = existingByCode.get();
        ou.setIsDeleted(Boolean.TRUE);
        ou.setDeletedAt(LocalDateTime.now());
        organizationUnitAppService.persistOrganizationUnit(ou);
    }

    /**
     * 按科室 id 列表批量切换组织单元启用状态。
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
            String code = OrganizationUnitLinkCode.fromOrganization(organization);
            if (code == null || code.isBlank()) {
                continue;
            }
            Optional<OrganizationUnit> existingByCode = organizationUnitAppService.findOptionalByCode(code);
            if (existingByCode.isEmpty()) {
                continue;
            }
            OrganizationUnit ou = existingByCode.get();
            ou.setIsEnabled(enabled);
            if (enabled) {
                restoreOuIfOrganizationActive(organization, ou);
            }
            organizationUnitAppService.persistOrganizationUnit(ou);
        }
    }

    /**
     * 将 HIS 组织快照字段写入 OU（不含 Whale 主键；parent 映射为上级 OU 的 id）。
     */
    private void applyOrganizationToOu(Organization organization, OrganizationUnit ou) {
        ou.setDisplayName(resolveOuDisplayName(organization));
        ou.setSortOrder(resolveSortOrder(organization.getDisplayOrder()));
        ou.setIsEnabled(Boolean.TRUE.equals(organization.getActiveFlag()));
        ou.setParentId(resolveOuParentId(organization));
    }

    /**
     * 解析 OU 父节点：按上级组织的 code 查找已同步的 {@link OrganizationUnit}，返回其 Whale id。
     */
    private Long resolveOuParentId(Organization organization) {
        if (organization == null || organization.getParentId() == null) {
            return null;
        }
        Organization parentOrg = organizationQueryService.loadOrganizationByIdForDisplay(organization.getParentId());
        if (parentOrg == null) {
            return null;
        }
        String parentCode = OrganizationUnitLinkCode.fromOrganization(parentOrg);
        if (parentCode == null || parentCode.isBlank()) {
            return null;
        }
        return organizationUnitAppService.findOptionalByCode(parentCode)
            .map(OrganizationUnit::getId)
            .orElse(null);
    }

    /**
     * 组织启用且仍关联同一科室时，撤销 OU 软删标记（作废后重新启用场景）。
     */
    private void restoreOuIfOrganizationActive(Organization organization, OrganizationUnit ou) {
        if (organization == null || ou == null || !Boolean.TRUE.equals(organization.getActiveFlag())) {
            return;
        }
        ou.setIsDeleted(Boolean.FALSE);
        ou.setDeletedAt(null);
        ou.setDeletedBy(null);
    }

    /**
     * 防止新 code 已被其他科室占用（关联键冲突）。
     */
    private void assertOuCodeOwnedByOrganization(OrganizationUnit ou, Long organizationId, String code) {
        if (ou == null || organizationId == null || code == null || code.isBlank()) {
            return;
        }
        Optional<Long> linkedByPrefix = OrganizationUnitLinkCode.parseBusinessOrganizationId(code);
        if (linkedByPrefix.isPresent() && !linkedByPrefix.get().equals(organizationId)) {
            throw new ValidationException("组织单元编码已被其他科室占用: " + code);
        }
        if (linkedByPrefix.isEmpty()) {
            Organization owner = organizationQueryService.findFirstByBusNo(code.trim());
            if (owner != null && owner.getId() != null && !owner.getId().equals(organizationId)) {
                throw new ValidationException("组织单元编码已被其他科室占用: " + code);
            }
        }
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
        List<Practitioner> practitioners = practitionerRoleScopeLoader.listActivePractitionersForPlatformRole(
            WhalePlatformRoleCodeConstants.ROLE_DOCTOR, null);
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
    private void relocateDepartment(Long organizationId, Long newParentId, Long operatorId) {
        RelocateOrganizationCommand command = new RelocateOrganizationCommand();
        command.setOperatorId(operatorId);
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
     * 上级组织树形下拉（医院与科室全量，具体可选范围由层级校验约束）。
     *
     * @return 组织树根选项
     */
    private List<OptionDto> loadDepartmentOptions() {
        List<Organization> organizations = organizationQueryService.getOrganizationDictTree();
        return toOrganizationOptions(organizations);
    }

    /**
     * 校验组织层级：单租户仅允许一家根医院；科室必须挂在医院或医院下科室。
     *
     * @param typeEnumRaw 请求中的类型原始值
     * @param parentId    上级组织主键
     * @param current     编辑时的现有组织（用于请求未传 type 时回退）
     */
    private void validateOrganizationHierarchy(String typeEnumRaw, Long parentId, Organization current) {
        OrganizationType type = OrganizationType.resolveRaw(typeEnumRaw);
        if (type == null && current != null) {
            type = OrganizationType.resolveRaw(current.getTypeEnum());
        }
        if (type == null) {
            return;
        }
        Long selfId = null;
        if (current != null) {
            selfId = current.getId();
        }
        if (OrganizationType.HOSPITAL == type) {
            // 单租户仅允许维护一家医院，且须为根节点供科室挂载。
            if (organizationQueryService.existsHospitalExcluding(selfId)) {
                throw new ValidationException("当前租户已存在医院，不可再新建医院");
            }
            if (parentId != null) {
                throw new ValidationException("医院须为根节点，不可挂载上级组织");
            }
            return;
        }
        if (OrganizationType.DEPARTMENT == type) {
            // 科室必须挂在医院体系下，不允许无上级或悬挂在根节点。
            if (!organizationQueryService.existsHospitalExcluding(null)) {
                throw new ValidationException("请先创建医院，再维护科室");
            }
            if (parentId == null) {
                throw new ValidationException("科室必须选择上级组织");
            }
            if (selfId != null && parentId.equals(selfId)) {
                throw new ValidationException("上级组织不能是自身");
            }
            Organization parent = organizationQueryService.loadOrganizationByIdForDisplay(parentId);
            if (parent == null) {
                throw new ValidationException("上级组织不存在");
            }
            OrganizationType parentType = OrganizationType.resolveRaw(parent.getTypeEnum());
            if (OrganizationType.HOSPITAL != parentType
                && OrganizationType.DEPARTMENT != parentType) {
                throw new ValidationException("科室的上级只能是医院或科室");
            }
        }
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
        // 同级节点按 displayOrder 排序，供维护页上级树与各业务科室下拉一致展示。
        for (List<Organization> siblings : childrenByParentId.values()) {
            siblings.sort(Comparator
                .comparing(Organization::getDisplayOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Organization::getId, Comparator.nullsLast(Comparator.naturalOrder())));
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
        return buildOrganizationOptions(
            this.sortOrganizationsByDisplayOrder(rootOrganizations),
            childrenByParentId);
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
        List<Organization> orderedOrganizations = this.sortOrganizationsByDisplayOrder(organizations);
        for (Organization organization : orderedOrganizations) {
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
