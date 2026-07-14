/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.position.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.health.application.basic.position.dto.*;
import com.openhis.health.application.basic.position.mapper.PositionMapper;
import com.openhis.health.application.basic.position.support.PositionPractitionerRoleWriteGate;
import com.openhis.health.application.basic.position.support.PositionRoleApiCompat;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.health.application.shared.validator.WhaleRoleResolver;
import com.openhis.health.application.basic.position.support.StaffManagedScopeWriteGate;
import com.openhis.health.application.basic.administration.support.PractitionerRoleScopeLoader;
import com.openhis.health.infrastructure.platform.identity.UserLoginOrganizationSyncService;
import com.openhis.health.infrastructure.platform.organization.OrganizationUnitLinkSyncService;
import com.openhis.health.domain.administration.healthcareservice.entity.HealthcareService;
import com.openhis.health.domain.administration.healthcareservice.service.HealthcareServiceQueryService;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.command.CreatePractitionerCommand;
import com.openhis.health.domain.administration.practitioner.command.EditPractitionerCommand;
import com.openhis.health.domain.administration.practitioner.command.SwitchOrganizationCommand;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerDomainService;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.administration.practitionerrole.command.*;
import com.openhis.health.domain.administration.practitionerrole.entity.PractitionerRole;
import com.openhis.health.domain.administration.practitionerrole.entity.PractitionerRoleServiceItem;
import com.openhis.health.domain.administration.practitionerrole.repository.PractitionerRoleServiceItemRepository;
import com.openhis.health.domain.administration.practitionerrole.service.PractitionerRoleDomainService;
import com.openhis.health.domain.administration.practitionerrole.service.PractitionerRoleQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants;
import com.openhis.health.domain.share.enums.AdministrativeGender;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.infrastructure.multitenancy.PlatformTenantAccessPolicy;
import com.openhis.whale.core.infrastructure.permission.RequirePermission;
import com.openhis.whale.identity.application.BuiltInBusinessRoleOptionLoader;
import com.openhis.whale.identity.application.OrganizationUnitAppService;
import com.openhis.whale.identity.application.RoleAppService;
import com.openhis.whale.identity.application.RolePlatformConstants;
import com.openhis.whale.identity.application.RoleStaffScopeConstants;
import com.openhis.whale.identity.application.UserAppService;
import com.openhis.whale.identity.application.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.Locale;

/**
 * 岗位管理应用服务：工作台编排、单条岗位 CRUD，以及用户+医务人员+六类岗位一体维护。
 *
 * <p>领域依据：{@code adm_pr_assign}（执业角色分配）与 DM-ADM-006；写侧委托
 * {@link PractitionerRoleDomainService} / {@link PractitionerDomainService}。
 * 可维护块由平台角色 {@link RoleStaffScopeConstants} 控制；登录科室派生同步至
 * {@code whale_user_organization_units}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PositionAppService {

    private final UserAppService userAppService;
    private final RoleAppService roleAppService;
    private final OrganizationUnitAppService organizationUnitAppService;
    private final PractitionerQueryService practitionerQueryService;
    private final PractitionerDomainService practitionerDomainService;
    private final PractitionerRoleQueryService practitionerRoleQueryService;
    private final PractitionerRoleDomainService practitionerRoleDomainService;
    private final PractitionerRoleServiceItemRepository practitionerRoleServiceItemRepository;
    private final HealthcareServiceQueryService healthcareServiceQueryService;
    private final LocationQueryService locationQueryService;
    private final PositionMapper positionMapper;
    private final CurrentOperatorResolver currentOperatorResolver;
    private final StaffManagedScopeWriteGate staffManagedScopeWriteGate;
    private final OrganizationUnitLinkSyncService organizationUnitLinkSyncService;
    private final UserLoginOrganizationSyncService userLoginOrganizationSyncService;
    private final PractitionerRoleScopeLoader practitionerRoleScopeLoader;
    private final BuiltInBusinessRoleOptionLoader builtInBusinessRoleOptionLoader;
    private final WhaleRoleResolver whaleRoleResolver;
    private final PositionUserRoleSyncService positionUserRoleSyncService;
    private final PositionPractitionerRoleWriteGate positionPractitionerRoleWriteGate;
    private final OrganizationQueryService organizationQueryService;
    private final PlatformTenantAccessPolicy platformTenantAccessPolicy;

    /**
     * 岗位工作台左侧：分页查询系统用户，并装配系统角色名与绑定医务人员主档（若存在）。
     *
     * @param request 用户查询与分页条件
     * @return 工作台用户行分页结果
     */
    @RequirePermission("Navigation.PositionManagement")
    public PagedResult<PositionUserWorkbenchRowDto> pageUsersForWorkbench(UserQueryRequest request) {
        // Gate：请求体缺省时补空查询；仅查启用用户
        if (request == null) {
            request = new UserQueryRequest();
        }
        request.setIsActive(Boolean.TRUE);
        // Pre：identity 分页拉取用户
        PagedResult<UserDto> userPage = userAppService.getList(request);
        List<PositionUserWorkbenchRowDto> rows = new ArrayList<>();
        if (userPage.getContent() == null) {
            return PagedResult.of(rows, userPage.getTotalCount(), userPage.getPageNo(), userPage.getPageSize());
        }
        // Pre 续：逐行 enrich 角色与绑定医务人员
        for (UserDto u : userPage.getContent()) {
            List<RoleDto> roles = userAppService.getRoles(u.getId());
            Practitioner practitioner = practitionerQueryService.findByUserId(u.getId());
            rows.add(positionMapper.toWorkbenchRow(u, roles, practitioner));
        }
        // Asm：映射工作台分页响应
        return PagedResult.of(rows, userPage.getTotalCount(), userPage.getPageNo(), userPage.getPageSize());
    }

    /**
     * 岗位分配可选内置角色（医师/护士/药师/库管/财务等），供岗位维护页下拉使用。
     *
     * @return 内置业务角色选项（含岗位范围声明）
     */
    @RequirePermission("Navigation.PositionManagement")
    public List<RoleOptionDto> listAssignmentRoleOptions() {
        // Gate：只读内置角色选项，须岗位管理权限
        // Pre/Asm：加载可分配内置业务角色
        return builtInBusinessRoleOptionLoader.loadAssignmentRoleOptions();
    }

    /**
     * 列出指定医务人员的全部岗位分配行。
     *
     * @param practitionerId 医务人员主键
     * @return 岗位分配行列表（含号源 enrich）
     * @throws ValidationException 医务人员标识为空时
     */
    @RequirePermission("Navigation.PositionManagement")
    public List<PractitionerAssignmentRowDto> listPractitionerAssignments(Long practitionerId) {
        // Gate：医务人员主键非空
        if (practitionerId == null) {
            throw new ValidationException("医务人员标识不能为空");
        }
        // Pre：读侧加载全部岗位分配
        List<PractitionerRole> roles = practitionerRoleQueryService.listAllRolesByPractitioner(practitionerId);
        roles.sort(Comparator.comparing(PractitionerRole::getId, Comparator.nullsLast(Long::compareTo)));
        List<Long> assignmentIds = roles.stream()
                .map(PractitionerRole::getId)
                .filter(id -> id != null)
                .toList();
        Map<Long, List<Long>> serviceIdsByAssignmentId = loadServiceIdsByAssignmentIds(assignmentIds);
        Set<Long> allServiceIds = new LinkedHashSet<>();
        for (List<Long> ids : serviceIdsByAssignmentId.values()) {
            if (ids != null) {
                allServiceIds.addAll(ids);
            }
        }
        Map<Long, HealthcareService> serviceMap = healthcareServiceQueryService.loadHealthcareServiceMapByIds(allServiceIds);
        List<PractitionerAssignmentRowDto> out = new ArrayList<>();
        for (PractitionerRole r : roles) {
            PractitionerAssignmentRowDto row = positionMapper.toAssignmentRow(r);
            List<Long> serviceIds = serviceIdsByAssignmentId.get(r.getId());
            if (serviceIds == null) {
                serviceIds = List.of();
            }
            row.setServiceIds(serviceIds);
            row.setServiceNames(formatServiceNames(serviceIds, serviceMap));
            out.add(row);
        }
        // Asm：映射岗位分配行列表
        return out;
    }

    /**
     * 当前登录人可维护的岗位人员信息块（StaffScope 并集）。
     * <p>仅依据操作者自身角色声明返回，不暴露他人数据，故仅需登录态。</p>
     *
     * @return 可编辑 StaffScope 集合
     */
    public StaffEditableScopesDto getEditableScopesForCurrentUser() {
        // Gate：须已登录
        Long operatorId = currentOperatorResolver.requireOperatorId(
                ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT, "请先登录");
        StaffEditableScopesDto dto = new StaffEditableScopesDto();
        dto.setEditableScopes(new ArrayList<>(resolveEditableScopes(operatorId)));
        // Asm：映射可编辑范围响应
        return dto;
    }

    /**
     * 按系统用户主键加载岗位人员维护详情（用户管理维护入口，仅需登录态）。
     *
     * @param userId 系统用户主键
     * @return 人员维护详情（含六类岗位块）
     * @throws ValidationException 用户标识为空时
     */
    public StaffDetailDto getStaffDetail(Long userId) {
        // Gate：用户主键非空
        if (userId == null) {
            throw new ValidationException("用户标识不能为空");
        }
        // Pre：加载用户、平台角色与绑定医务人员
        IdRequest idRequest = new IdRequest();
        idRequest.setId(userId);
        UserDto user = userAppService.getById(idRequest);
        Practitioner practitioner = practitionerQueryService.findByUserId(userId);

        StaffDetailDto detail = new StaffDetailDto();
        detail.setUserId(user.getId());
        detail.setUserName(user.getUserName());
        detail.setName(user.getName());
        detail.setEmail(user.getEmail());
        detail.setPhoneNumber(user.getPhoneNumber());
        detail.setIsActive(user.getIsActive());
        detail.setSystemRoles(userAppService.getRoles(userId));

        if (practitioner != null) {
            detail.setPractitionerId(practitioner.getId());
            detail.setPractitionerName(practitioner.getName());
            detail.setBusNo(practitioner.getBusNo());
            detail.setYbNo(practitioner.getYbNo());
            detail.setDrProfttlCode(practitioner.getDrProfttlCode());
            detail.setPharPracCertNo(practitioner.getPharPracCertNo());
            detail.setGenderEnum(practitioner.getGenderEnum());
            detail.setStatusEnum(practitioner.getStatusEnum());
            detail.setPrimaryOrgId(practitioner.getOrgId());
            // Pre 续：装配六类岗位分配块
            fillAssignmentBlocks(detail, practitioner.getId());
        }
        // Asm：映射人员维护详情
        return detail;
    }

    /**
     * 保存岗位人员：新建或更新用户、平台角色、医务人员与六类岗位分配。
     *
     * @param request 人员维护请求
     * @return 保存后最新详情
     * @throws ValidationException 请求体为空或字段校验失败时
     */
    public StaffDetailDto saveStaff(SaveStaffRequest request) {
        // Gate：请求体非空
        if (request == null) {
            throw new ValidationException("请求不能为空");
        }
        // Gate 续：写路径操作人与可编辑 StaffScope
        Long operatorId = currentOperatorResolver.requireOperatorId(
                ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT, "请先登录后再维护人员");
        Set<String> editableScopes = new LinkedHashSet<>(resolveEditableScopes(operatorId));

        // Pre：解析或新建平台用户
        Long userId = resolvePlatformUserId(request);
        UserDto user;
        // 业务分支：无既有用户则走新建账号
        if (userId == null) {
            if (StringUtils.isEmpty(request.getUserName())) {
                throw new ValidationException("账号不能为空");
            }
            if (StringUtils.isEmpty(request.getPassword())) {
                throw new ValidationException("密码不能为空");
            }
            CreateUserRequest createUserRequest = new CreateUserRequest();
            createUserRequest.setUserName(request.getUserName().trim());
            createUserRequest.setPassword(request.getPassword());
            createUserRequest.setName(request.getName());
            createUserRequest.setEmail(request.getEmail());
            createUserRequest.setPhoneNumber(request.getPhoneNumber());
            if (request.getIsActive() != null) {
                createUserRequest.setIsActive(request.getIsActive());
            }
            user = userAppService.create(createUserRequest);
            userId = user.getId();
        } else {
            // 业务分支：更新既有用户基础信息
            UpdateUserRequest updateUserRequest = new UpdateUserRequest();
            updateUserRequest.setName(request.getName());
            updateUserRequest.setEmail(request.getEmail());
            updateUserRequest.setPhoneNumber(request.getPhoneNumber());
            updateUserRequest.setIsActive(request.getIsActive());
            user = userAppService.update(userId, updateUserRequest);
        }

        Practitioner practitioner = practitionerQueryService.findByUserId(userId);
        Long practitionerId = request.getPractitionerId();
        String genderEnum = normalizeGenderEnum(request.getGenderEnum());
        String ybNo = resolveStaffTextField(request, "ybNo", request.getYbNo());
        Integer drProfttlCode = resolveStaffDrProfttlCode(request);
        String pharPracCertNo = resolveStaffTextField(request, "pharPracCertNo", request.getPharPracCertNo());
        // 业务分支：无绑定医务人员则新建，否则编辑
        if (practitioner == null) {
            CreatePractitionerCommand createCmd = new CreatePractitionerCommand();
            createCmd.setOperatorId(operatorId);
            CreatePractitionerCommand.CreatePractitionerItemCommand item =
                    new CreatePractitionerCommand.CreatePractitionerItemCommand();
            item.setUserId(userId);
            item.setName(resolvePractitionerName(request, user));
            item.setGenderEnum(genderEnum);
            item.setBusNo(request.getBusNo());
            item.setOrgId(request.getPrimaryOrgId());
            item.setYbNo(ybNo);
            item.setDrProfttlCode(drProfttlCode);
            item.setPharPracCertNo(pharPracCertNo);
            createCmd.setItem(item);
            practitionerId = practitionerDomainService.createPractitioner(createCmd);
        } else {
            practitionerId = practitioner.getId();
            EditPractitionerCommand editCmd = new EditPractitionerCommand();
            editCmd.setOperatorId(operatorId);
            EditPractitionerCommand.EditPractitionerItemCommand editItem =
                    new EditPractitionerCommand.EditPractitionerItemCommand();
            editItem.setPractitionerId(practitionerId);
            editItem.setName(resolvePractitionerName(request, user));
            editItem.setGenderEnum(genderEnum);
            editItem.setPhone(request.getPhoneNumber());
            editItem.setBusNo(request.getBusNo());
            editItem.setYbNo(ybNo);
            editItem.setDrProfttlCode(drProfttlCode);
            editItem.setPharPracCertNo(pharPracCertNo);
            editCmd.setItem(editItem);
            practitionerDomainService.editPractitioner(editCmd);
            // 业务分支：显式同步默认登录科室
            if (Boolean.TRUE.equals(request.getSyncPrimaryOrgId()) && request.getPrimaryOrgId() != null) {
                syncPrimaryOrgId(practitionerId, operatorId, practitioner.getOrgId(), request.getPrimaryOrgId());
            }
        }

        String practitionerName = resolvePractitionerName(request, user);
        Set<String> roleStaffScopes = resolveStaffScopesForRoleIds(request.getRoleIds());
        // Cmd：按 StaffScope 全量替换六类岗位块
        replaceStaffScopeBlocks(practitionerId, operatorId, request, practitionerName, editableScopes, roleStaffScopes);

        // Cmd 续：同步用户可登录科室
        organizationUnitAppService.syncOrganizationUnitsForUser(userId,
                organizationUnitLinkSyncService.resolveWhaleOrganizationUnitIds(
                        collectLoginOrganizationIds(practitionerId)));

        // 平台登录角色须在岗位块 replace 之后写入，否则 afterReplaceBlock 会按空岗位撤销 whale_user_roles。
        // admin 自动拥有全部角色；其它用户 roleIds 为空时表示未改角色。
        // 业务分支：admin 自动拥有全部角色；其它用户 roleIds 为空表示未改角色
        if (user.getUserName() != null && "admin".equalsIgnoreCase(user.getUserName().trim())) {
            userAppService.syncAllRolesForAdminUser(userId);
        } else if (request.getRoleIds() != null && !request.getRoleIds().isEmpty()) {
            AssignRolesRequest assignRolesRequest = new AssignRolesRequest();
            assignRolesRequest.setRoleIds(request.getRoleIds());
            userAppService.assignRoles(userId, assignRolesRequest);
        }

        // Asm：回读保存后人员详情
        return getStaffDetail(userId);
    }

    /**
     * 按机构加载位置下拉选项。
     *
     * @param organizationId 机构主键
     * @return 位置选项列表
     */
    @RequirePermission("Navigation.PositionManagement")
    public List<OptionDto> listLocationOptionsByOrganization(Long organizationId) {
        // Pre/Asm：读侧加载机构下启用位置并映射选项
        return positionMapper.toSortedLocationOptions(locationQueryService.listActiveLocationsByOrganization(organizationId));
    }

    /**
     * 按位置主键加载单条位置选项。
     *
     * @param locationId 位置主键
     * @return 位置选项；不存在时 null
     */
    @RequirePermission("Navigation.PositionManagement")
    public OptionDto getLocationOptionById(Long locationId) {
        // Pre/Asm：读侧可选加载并映射单条选项
        return locationQueryService
                .findOptionalById(locationId)
                .map(loc -> positionMapper.toLocationOption(loc, 0L))
                .orElse(null);
    }

    /**
     * 分配执业角色（岗位）。
     *
     * @param cmd 分配命令
     * @return 新建分配主键
     */
    @RequirePermission("Navigation.PositionManagement")
    public Long assignPractitionerRole(AssignPractitionerRoleCommand cmd) {
        // Gate：写路径操作人
        cmd.setOperatorId(currentOperatorResolver.requireOperatorId(
                ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_ARGUMENT, "请先登录后再操作岗位"));
        // Pre：归一化平台角色 id 并校验分配项
        normalizeAssignCommandItems(cmd);
        positionPractitionerRoleWriteGate.assertAssignItemsValid(cmd.resolveItems());
        // Cmd：领域分配岗位
        Long assignmentId = practitionerRoleDomainService.assignRole(cmd);
        for (AssignPractitionerRoleCommand.AssignPractitionerRoleItemCommand item : cmd.resolveItems()) {
            if (item != null && item.getPractitionerId() != null && item.getRoleId() != null) {
                positionUserRoleSyncService.onAssignmentAdded(item.getPractitionerId(), item.getRoleId());
            }
        }
        // Cmd 续：同步登录科室
        this.syncLoginOrganizationsFromAssignCommand(cmd);
        return assignmentId;
    }

    /**
     * 编辑执业角色（岗位）；库房/药房走专用 location 同步路径。
     *
     * @param cmd 编辑命令
     */
    @RequirePermission("Navigation.PositionManagement")
    public void editPractitionerRole(EditPractitionerRoleCommand cmd) {
        // Gate：写路径操作人
        cmd.setOperatorId(currentOperatorResolver.requireOperatorId(
                ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_ARGUMENT, "请先登录后再操作岗位"));
        List<EditPractitionerRoleCommand.EditPractitionerRoleItemCommand> resolved = cmd.resolveItems();
        List<EditPractitionerRoleCommand.EditPractitionerRoleItemCommand> normalItems = new ArrayList<>();
        for (EditPractitionerRoleCommand.EditPractitionerRoleItemCommand item : resolved) {
            // 业务分支：库房/药房岗位走 inventory 专用同步
            if (item != null && isInventoryAssignment(item.getAssignmentId())) {
                syncInventoryRoleLocations(cmd.getOperatorId(), item);
            } else if (item != null) {
                normalItems.add(item);
            }
        }
        if (!normalItems.isEmpty()) {
            // Cmd：领域编辑普通岗位项
            EditPractitionerRoleCommand normalCmd = new EditPractitionerRoleCommand();
            normalCmd.setOperatorId(cmd.getOperatorId());
            normalCmd.setItems(normalItems);
            practitionerRoleDomainService.editRole(normalCmd);
            syncAssignmentServicesFromEditCommand(normalCmd);
        }
        // Cmd 续：同步登录科室
        this.syncLoginOrganizationsFromAssignmentCommand(cmd);
    }

    /**
     * 废止执业角色（岗位）分配。
     *
     * @param cmd 废止命令
     */
    @RequirePermission("Navigation.PositionManagement")
    public void retirePractitionerRole(RetirePractitionerRoleCommand cmd) {
        // Gate：写路径操作人
        cmd.setOperatorId(currentOperatorResolver.requireOperatorId(
                ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_ARGUMENT, "请先登录后再操作岗位"));
        // Pre：收集待废止分配 id
        List<RetirePractitionerRoleCommand.RetirePractitionerRoleItemCommand> items = cmd.resolveItems();
        List<Long> assignmentIds = new ArrayList<>();
        for (RetirePractitionerRoleCommand.RetirePractitionerRoleItemCommand item : items) {
            if (item != null && item.getAssignmentId() != null) {
                assignmentIds.add(item.getAssignmentId());
            }
        }
        // Cmd：领域废止岗位
        practitionerRoleDomainService.retireRole(cmd);
        // Cmd 续：同步平台用户角色与登录科室
        syncUserRolesAfterRetire(assignmentIds);
        this.syncLoginOrganizationsFromAssignmentCommand(cmd);
    }

    /**
     * 启用执业角色（岗位）分配。
     *
     * @param cmd 启用命令
     */
    @RequirePermission("Navigation.PositionManagement")
    public void activatePractitionerRole(ActivatePractitionerRoleCommand cmd) {
        // Gate：写路径操作人
        cmd.setOperatorId(currentOperatorResolver.requireOperatorId(
                ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_ARGUMENT, "请先登录后再操作岗位"));
        // Cmd：领域启用岗位
        practitionerRoleDomainService.activateRole(cmd);
        // Cmd 续：同步登录科室
        this.syncLoginOrganizationsFromAssignmentCommand(cmd);
    }

    /**
     * 停用执业角色（岗位）分配。
     *
     * @param cmd 停用命令
     */
    @RequirePermission("Navigation.PositionManagement")
    public void deactivatePractitionerRole(DeactivatePractitionerRoleCommand cmd) {
        // Gate：写路径操作人
        cmd.setOperatorId(currentOperatorResolver.requireOperatorId(
                ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_ARGUMENT, "请先登录后再操作岗位"));
        // Cmd：领域停用岗位
        practitionerRoleDomainService.deactivateRole(cmd);
        // Cmd 续：同步登录科室
        this.syncLoginOrganizationsFromAssignmentCommand(cmd);
    }

    /**
     * 编辑岗位时若携带号源列表，增量同步 {@code adm_practitioner_role_service_item} 子表。
     *
     * @param cmd 编辑命令（含 assignmentId 与 serviceIds 的行）
     */
    private void syncAssignmentServicesFromEditCommand(EditPractitionerRoleCommand cmd) {
        if (cmd == null) {
            return;
        }
        List<EditPractitionerRoleCommand.EditPractitionerRoleItemCommand> pending = new ArrayList<>();
        for (EditPractitionerRoleCommand.EditPractitionerRoleItemCommand item : cmd.resolveItems()) {
            if (item != null && item.getServiceIds() != null) {
                pending.add(item);
            }
        }
        if (pending.isEmpty()) {
            return;
        }
        UpdatePractitionerRoleServicesCommand updateCmd = new UpdatePractitionerRoleServicesCommand();
        updateCmd.setOperatorId(cmd.getOperatorId());
        updateCmd.setItems(new ArrayList<>());
        for (EditPractitionerRoleCommand.EditPractitionerRoleItemCommand item : pending) {
            UpdatePractitionerRoleServicesCommand.UpdatePractitionerRoleServicesItemCommand serviceItem =
                    new UpdatePractitionerRoleServicesCommand.UpdatePractitionerRoleServicesItemCommand();
            serviceItem.setAssignmentId(item.getAssignmentId());
            serviceItem.setServiceIds(item.getServiceIds());
            updateCmd.getItems().add(serviceItem);
        }
        positionPractitionerRoleWriteGate.assertUpdateServicesValid(updateCmd);
        practitionerRoleDomainService.updateServices(updateCmd);
    }

    /**
     * 批量加载岗位分配 id → 可接诊号源 id 列表（展示与编辑回显用）。
     *
     * @param assignmentIds 岗位分配主键集合
     * @return assignmentId → healthcareServiceId 列表
     */
    private Map<Long, List<Long>> loadServiceIdsByAssignmentIds(List<Long> assignmentIds) {
        Map<Long, List<Long>> result = new HashMap<>();
        if (assignmentIds == null || assignmentIds.isEmpty()) {
            return result;
        }
        List<PractitionerRoleServiceItem> rows =
                practitionerRoleServiceItemRepository.findAllByAssignmentIdIn(assignmentIds);
        for (PractitionerRoleServiceItem row : rows) {
            if (row == null || row.getAssignmentId() == null || row.getHealthcareServiceId() == null) {
                continue;
            }
            result.computeIfAbsent(row.getAssignmentId(), key -> new ArrayList<>())
                    .add(row.getHealthcareServiceId());
        }
        return result;
    }

    /**
     * 将号源 id 列表格式化为顿号分隔的展示名称；缺失名称时回落为 id 字符串。
     *
     * @param serviceIds  号源主键
     * @param serviceMap  id → HealthcareService 缓存
     * @return 展示文案；无有效项时空串
     */
    private String formatServiceNames(List<Long> serviceIds, Map<Long, HealthcareService> serviceMap) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            return "";
        }
        return serviceIds.stream()
                .map(id -> {
                    HealthcareService service = serviceMap.get(id);
                    if (service == null || StringUtils.isEmpty(service.getName())) {
                        if (id == null) {
                            return "";
                        }
                        return String.valueOf(id);
                    }
                    return service.getName().trim();
                })
                .filter(name -> !name.isBlank())
                .collect(Collectors.joining("、"));
    }

    /**
     * 解析操作者可编辑的人员岗位块 scope 集合（平台管理员或 admin 角色拥有全部内置块）。
     *
     * @param operatorId 当前操作人
     * @return 可编辑的 {@link RoleStaffScopeConstants} code 集合
     */
    private Set<String> resolveEditableScopes(Long operatorId) {
        if (platformTenantAccessPolicy != null && platformTenantAccessPolicy.isPlatformAdmin(operatorId)) {
            return allStaffScopeCodes();
        }
        List<RoleDto> roles = userAppService.getRoles(operatorId);
        for (RoleDto role : roles) {
            if (role == null) {
                continue;
            }
            if (RolePlatformConstants.CODE_ADMIN.equalsIgnoreCase(role.getCode())
                    || "admin".equalsIgnoreCase(role.getName())) {
                return allStaffScopeCodes();
            }
        }

        Set<String> scopes = new LinkedHashSet<>();
        for (RoleDto role : roles) {
            if (role == null || role.getId() == null) {
                continue;
            }
            List<RoleClaimDto> roleClaims = roleAppService.getClaims(role.getId());
            for (RoleClaimDto claim : roleClaims) {
                if (claim == null) {
                    continue;
                }
                if (!RoleStaffScopeConstants.CLAIM_TYPE.equals(claim.getClaimType())) {
                    continue;
                }
                if (claim.getClaimValue() != null && !claim.getClaimValue().isBlank()) {
                    scopes.add(claim.getClaimValue().trim());
                }
            }
        }
        return scopes;
    }

    /**
     * 内置五类人员岗位块 scope 全集（医师出诊/库房/药房/护理科室/护理病区）。
     *
     * @return 内置 scope code 集合
     */
    private static Set<String> allStaffScopeCodes() {
        Set<String> scopes = new LinkedHashSet<>();
        scopes.add(RoleStaffScopeConstants.DOCTOR_VISIT_ORG);
        scopes.add(RoleStaffScopeConstants.WAREHOUSE_LOCATION);
        scopes.add(RoleStaffScopeConstants.PHARMACY_LOCATION);
        scopes.add(RoleStaffScopeConstants.NURSE_ORG);
        scopes.add(RoleStaffScopeConstants.NURSE_WARD_LOCATION);
        return scopes;
    }

    /**
     * 按平台角色将有效岗位分配填入人员详情六类块（含医师号源 id 汇总）。
     *
     * @param detail          待填充详情 DTO
     * @param practitionerId  医务人员主键
     */
    private void fillAssignmentBlocks(StaffDetailDto detail, Long practitionerId) {
        List<PractitionerRole> roles = practitionerRoleQueryService.listAllRolesByPractitioner(practitionerId);
        List<StaffOrgLocationItemDto> doctorVisit = new ArrayList<>();
        List<StaffOrgLocationItemDto> warehouse = new ArrayList<>();
        List<StaffOrgLocationItemDto> pharmacy = new ArrayList<>();
        List<StaffOrgLocationItemDto> nurseOrg = new ArrayList<>();
        List<StaffOrgLocationItemDto> nurseWard = new ArrayList<>();
        Set<Long> doctorServiceIds = new LinkedHashSet<>();
        List<Long> doctorAssignmentIds = new ArrayList<>();

        for (PractitionerRole role : roles) {
            if (Boolean.TRUE.equals(role.getIsDeleted()) || !Boolean.TRUE.equals(role.getActiveFlag())) {
                continue;
            }
            StaffOrgLocationItemDto row = new StaffOrgLocationItemDto();
            row.setOrgId(role.getOrgId());
            row.setLocationId(role.getLocationId());
            Long roleId = role.getRoleId();
            if (whaleRoleResolver.matchesPlatformRoleId(roleId, WhalePlatformRoleCodeConstants.ROLE_DOCTOR)) {
                doctorVisit.add(row);
                if (role.getId() != null) {
                    doctorAssignmentIds.add(role.getId());
                }
            } else if (whaleRoleResolver.matchesPlatformRoleId(roleId, WhalePlatformRoleCodeConstants.ROLE_WAREHOUSE)) {
                warehouse.add(row);
            } else if (whaleRoleResolver.matchesPlatformRoleId(roleId, WhalePlatformRoleCodeConstants.ROLE_PHARMACIST)) {
                pharmacy.add(row);
            } else if (whaleRoleResolver.matchesPlatformRoleId(roleId, WhalePlatformRoleCodeConstants.ROLE_NURSE)) {
                if (role.getLocationId() != null) {
                    nurseWard.add(row);
                } else {
                    nurseOrg.add(row);
                }
            }
        }
        detail.setDoctorVisitOrgList(doctorVisit);
        detail.setWarehouseLocationList(warehouse);
        detail.setPharmacyLocationList(pharmacy);
        detail.setNurseOrgList(nurseOrg);
        detail.setNurseWardLocationList(nurseWard);
        if (!doctorAssignmentIds.isEmpty()) {
            Map<Long, List<Long>> serviceIdsByAssignmentId = loadServiceIdsByAssignmentIds(doctorAssignmentIds);
            for (List<Long> ids : serviceIdsByAssignmentId.values()) {
                if (ids != null) {
                    doctorServiceIds.addAll(ids);
                }
            }
        }
        detail.setDoctorServiceIds(new ArrayList<>(doctorServiceIds));
    }

    /**
     * 按请求角色与操作者可编辑块，全量替换或清空六类岗位分配。
     */
    private void replaceStaffScopeBlocks(
            Long practitionerId,
            Long operatorId,
            SaveStaffRequest request,
            String practitionerName,
            Set<String> editableScopes,
            Set<String> roleStaffScopes) {
        if (shouldReplaceStaffScope(RoleStaffScopeConstants.DOCTOR_VISIT_ORG, editableScopes, roleStaffScopes)) {
            List<StaffOrgLocationItemDto> rows = request.getDoctorVisitOrgList();
            staffManagedScopeWriteGate.assertWritableOrganizationIds(collectOrganizationIds(rows));
            List<Long> serviceIds = request.getDoctorServiceIds();
            if (rows != null && !rows.isEmpty() && (serviceIds == null || serviceIds.isEmpty())) {
                throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_RULE_CONFLICT,
                        "医师出诊须配置可接诊号源");
            }
            replaceBlock(practitionerId, operatorId, RoleStaffScopeConstants.DOCTOR_VISIT_ORG,
                    rows, practitionerName, true, serviceIds);
        }
        if (shouldReplaceStaffScope(RoleStaffScopeConstants.WAREHOUSE_LOCATION, editableScopes, roleStaffScopes)) {
            List<StaffOrgLocationItemDto> rows = request.getWarehouseLocationList();
            staffManagedScopeWriteGate.assertWritableLocationIds(collectLocationIds(rows));
            replaceBlock(practitionerId, operatorId, RoleStaffScopeConstants.WAREHOUSE_LOCATION,
                    rows, practitionerName, false, null);
        }
        if (shouldReplaceStaffScope(RoleStaffScopeConstants.PHARMACY_LOCATION, editableScopes, roleStaffScopes)) {
            List<StaffOrgLocationItemDto> rows = request.getPharmacyLocationList();
            staffManagedScopeWriteGate.assertWritableLocationIds(collectLocationIds(rows));
            replaceBlock(practitionerId, operatorId, RoleStaffScopeConstants.PHARMACY_LOCATION,
                    rows, practitionerName, false, null);
        }
        if (shouldReplaceStaffScope(RoleStaffScopeConstants.NURSE_WARD_LOCATION, editableScopes, roleStaffScopes)) {
            List<StaffOrgLocationItemDto> nurseItems;
            if (request.getNurseWardLocationList() != null) {
                nurseItems = new ArrayList<>(request.getNurseWardLocationList());
            } else {
                nurseItems = List.of();
            }
            List<StaffOrgLocationItemDto> nurseWardOnly = new ArrayList<>();
            for (StaffOrgLocationItemDto item : nurseItems) {
                if (item != null && item.getLocationId() != null) {
                    nurseWardOnly.add(item);
                }
            }
            staffManagedScopeWriteGate.assertWritableLocationIds(collectLocationIds(nurseWardOnly));
            replaceBlock(practitionerId, operatorId, RoleStaffScopeConstants.NURSE_ORG,
                    nurseItems, practitionerName, true, null);
        }
    }

    /**
     * 判定本次保存是否应全量替换指定岗位块（须同时满足可编辑、角色授权且为内置块）。
     *
     * @param staffScope       岗位块 scope code
     * @param editableScopes   操作者可编辑 scope
     * @param roleStaffScopes  请求角色授权 scope
     * @return 应替换为 true
     */
    private static boolean shouldReplaceStaffScope(
            String staffScope, Set<String> editableScopes, Set<String> roleStaffScopes) {
        return editableScopes.contains(staffScope)
                && roleStaffScopes.contains(staffScope)
                && isBuiltinStaffScope(staffScope);
    }

    /**
     * 是否为系统内置五类人员岗位块 scope（非自定义扩展块）。
     *
     * @param staffScope scope code
     * @return 内置块为 true
     */
    private static boolean isBuiltinStaffScope(String staffScope) {
        return RoleStaffScopeConstants.DOCTOR_VISIT_ORG.equals(staffScope)
                || RoleStaffScopeConstants.WAREHOUSE_LOCATION.equals(staffScope)
                || RoleStaffScopeConstants.PHARMACY_LOCATION.equals(staffScope)
                || RoleStaffScopeConstants.NURSE_ORG.equals(staffScope)
                || RoleStaffScopeConstants.NURSE_WARD_LOCATION.equals(staffScope);
    }

    /**
     * 由请求绑定的平台角色 id 推导对应人员岗位块 scope（库房角色含药房块）。
     *
     * @param roleIds 平台角色 id 列表
     * @return 岗位块 scope code 集合
     */
    private Set<String> resolveStaffScopesForRoleIds(List<Long> roleIds) {
        Set<String> scopes = new LinkedHashSet<>();
        if (roleIds == null || roleIds.isEmpty()) {
            return scopes;
        }
        for (Long roleId : roleIds) {
            if (roleId == null) {
                continue;
            }
            String code = whaleRoleResolver.findRoleCodeById(roleId);
            if (code == null || code.isBlank()) {
                continue;
            }
            String normalized = code.trim().toUpperCase(Locale.ROOT);
            if (WhalePlatformRoleCodeConstants.ROLE_DOCTOR.equals(normalized)) {
                scopes.add(RoleStaffScopeConstants.DOCTOR_VISIT_ORG);
            } else if (WhalePlatformRoleCodeConstants.ROLE_NURSE.equals(normalized)) {
                scopes.add(RoleStaffScopeConstants.NURSE_WARD_LOCATION);
            } else if (WhalePlatformRoleCodeConstants.ROLE_PHARMACIST.equals(normalized)) {
                scopes.add(RoleStaffScopeConstants.PHARMACY_LOCATION);
            } else if (WhalePlatformRoleCodeConstants.ROLE_WAREHOUSE.equals(normalized)) {
                scopes.add(RoleStaffScopeConstants.WAREHOUSE_LOCATION);
                scopes.add(RoleStaffScopeConstants.PHARMACY_LOCATION);
            }
        }
        return scopes;
    }

    /**
     * 按岗位块 scope 全量替换同类型岗位分配（ReplaceByType），医师块可附带统一号源列表。
     *
     * @param practitionerId   医务人员
     * @param operatorId         操作人
     * @param staffScope         岗位块 scope
     * @param rows               组织/位置行
     * @param practitionerName   岗位展示名
     * @param orgMode            true 时 org 为主、location 可选；false 时 location 为主
     * @param serviceIds         医师出诊块号源（其他块传 null）
     * @throws ValidationException 未找到对应平台角色
     */
    private void replaceBlock(
            Long practitionerId,
            Long operatorId,
            String staffScope,
            List<StaffOrgLocationItemDto> rows,
            String practitionerName,
            boolean orgMode,
            List<Long> serviceIds) {
        Long roleId = PositionRoleApiCompat.defaultRoleIdForStaffScope(staffScope, whaleRoleResolver::findRoleIdByCode);
        if (roleId == null) {
            throw new ValidationException("未找到岗位块对应的平台角色：" + staffScope);
        }
        ReplacePractitionerRolesByTypeCommand cmd = new ReplacePractitionerRolesByTypeCommand();
        cmd.setPractitionerId(practitionerId);
        cmd.setRoleId(roleId);
        cmd.setOperatorId(operatorId);
        List<AssignPractitionerRoleCommand.AssignPractitionerRoleItemCommand> items = new ArrayList<>();
        if (rows != null) {
            for (StaffOrgLocationItemDto row : rows) {
                if (row == null) {
                    continue;
                }
                AssignPractitionerRoleCommand.AssignPractitionerRoleItemCommand item =
                        new AssignPractitionerRoleCommand.AssignPractitionerRoleItemCommand();
                item.setPractitionerId(practitionerId);
                item.setRoleId(roleId);
                item.setName(practitionerName);
                if (orgMode) {
                    item.setOrgId(row.getOrgId());
                    if (row.getLocationId() != null) {
                        item.setLocationId(row.getLocationId());
                    }
                } else {
                    item.setLocationId(row.getLocationId());
                    item.setOrgId(row.getOrgId());
                }
                if (RoleStaffScopeConstants.DOCTOR_VISIT_ORG.equals(staffScope)
                        && serviceIds != null
                        && !serviceIds.isEmpty()) {
                    item.setServiceIds(new ArrayList<>(serviceIds));
                }
                items.add(item);
            }
        }
        cmd.setItems(items);
        positionPractitionerRoleWriteGate.assertAssignItemsValid(cmd.getItems());
        practitionerRoleDomainService.replaceRolesByType(cmd);
        positionUserRoleSyncService.afterReplaceBlock(practitionerId, roleId);
    }

    /**
     * 合并护理科室与护理病区分配：不可编辑块保留存量，可编辑块取请求体。
     *
     * @param practitionerId  医务人员
     * @param request         保存请求
     * @param editableScopes  操作者可编辑 scope
     * @return 合并后的护理分配行（科室 + 病区）
     */
    private List<StaffOrgLocationItemDto> mergeNurseAssignments(
            Long practitionerId,
            SaveStaffRequest request,
            Set<String> editableScopes) {
        List<StaffOrgLocationItemDto> merged = new ArrayList<>();
        if (editableScopes.contains(RoleStaffScopeConstants.NURSE_ORG)) {
            if (request.getNurseOrgList() != null) {
                merged.addAll(request.getNurseOrgList());
            }
        } else {
            StaffDetailDto existing = new StaffDetailDto();
            fillAssignmentBlocks(existing, practitionerId);
            if (existing.getNurseOrgList() != null) {
                merged.addAll(existing.getNurseOrgList());
            }
        }
        if (editableScopes.contains(RoleStaffScopeConstants.NURSE_WARD_LOCATION)) {
            if (request.getNurseWardLocationList() != null) {
                merged.addAll(request.getNurseWardLocationList());
            }
        } else {
            StaffDetailDto existing = new StaffDetailDto();
            fillAssignmentBlocks(existing, practitionerId);
            if (existing.getNurseWardLocationList() != null) {
                merged.addAll(existing.getNurseWardLocationList());
            }
        }
        return merged;
    }

    /**
     * 从岗位分配与 {@code adm_practitioner.org_id} 收集可登录科室，勿依赖请求体局部字段以免编辑用户时误清空。
     */
    private List<Long> collectLoginOrganizationIds(Long practitionerId) {
        Set<Long> orgIds = new LinkedHashSet<>(
                practitionerRoleQueryService.collectLoginOrganizationUnitIds(practitionerId));
        Practitioner practitioner = practitionerQueryService.findById(practitionerId);
        if (practitioner != null && practitioner.getOrgId() != null) {
            orgIds.add(practitioner.getOrgId());
        }
        return new ArrayList<>(orgIds);
    }

    /**
     * 人员默认登录科室变更时同步 {@code adm_practitioner.org_id}（与请求 primaryOrgId 不一致才写）。
     *
     * @param practitionerId  医务人员
     * @param operatorId      操作人
     * @param currentOrgId    当前 org_id
     * @param primaryOrgId    目标默认科室
     * @throws ValidationException 目标科室不存在
     */
    private void syncPrimaryOrgId(
            Long practitionerId,
            Long operatorId,
            Long currentOrgId,
            Long primaryOrgId) {
        if (Objects.equals(currentOrgId, primaryOrgId)) {
            return;
        }
        if (primaryOrgId != null
                && organizationQueryService.loadOrganizationByIdForDisplay(primaryOrgId) == null) {
            throw new ValidationException("默认登录科室不存在");
        }
        SwitchOrganizationCommand switchCmd = new SwitchOrganizationCommand();
        switchCmd.setOperatorId(operatorId);
        SwitchOrganizationCommand.SwitchOrganizationItemCommand switchItem =
                new SwitchOrganizationCommand.SwitchOrganizationItemCommand();
        switchItem.setPractitionerId(practitionerId);
        switchItem.setTargetOrgId(primaryOrgId);
        switchCmd.setItem(switchItem);
        practitionerDomainService.switchOrganization(switchCmd);
    }

    /**
     * 从组织/位置行提取 orgId 并入目标集合（去重由 Set 保证）。
     *
     * @param target 目标 orgId 集合
     * @param rows   岗位分配行
     */
    private void addOrgIds(Set<Long> target, List<StaffOrgLocationItemDto> rows) {
        if (rows == null) {
            return;
        }
        for (StaffOrgLocationItemDto row : rows) {
            if (row != null && row.getOrgId() != null) {
                target.add(row.getOrgId());
            }
        }
    }

    /**
     * 解析可更新的平台用户主键：仅当 {@code whale_users} 中已存在时返回。
     *
     * <p>取消 Practitioner 与 User 共享主键后，请求里的 {@code userId} 可能误传医务人员 id；
     * 此时按 {@code user_id} 反查。均不存在则返回 {@code null}，走新建用户分支。</p>
     */
    private Long resolvePlatformUserId(SaveStaffRequest request) {
        if (request.getUserId() != null && userAppService.existsById(request.getUserId())) {
            return request.getUserId();
        }
        if (request.getUserId() != null) {
            Practitioner byRequestedId = practitionerQueryService.findById(request.getUserId());
            if (byRequestedId != null
                    && byRequestedId.getUserId() != null
                    && userAppService.existsById(byRequestedId.getUserId())) {
                return byRequestedId.getUserId();
            }
        }
        if (request.getPractitionerId() != null) {
            Practitioner byPractitionerId = practitionerQueryService.findById(request.getPractitionerId());
            if (byPractitionerId != null
                    && byPractitionerId.getUserId() != null
                    && userAppService.existsById(byPractitionerId.getUserId())) {
                return byPractitionerId.getUserId();
            }
        }
        return null;
    }

    /**
     * 解析保存请求中的医务人员展示名：请求名优先，其次平台用户名。
     *
     * @param request 保存请求
     * @param user    已解析的平台用户（可 null）
     * @return 非空展示名；均无则回落 userName
     */
    private String resolvePractitionerName(SaveStaffRequest request, UserDto user) {
        if (StringUtils.isNotEmpty(request.getName())) {
            return request.getName().trim();
        }
        if (user != null && StringUtils.isNotEmpty(user.getName())) {
            return user.getName().trim();
        }
        return request.getUserName();
    }

    private static final ObjectMapper STAFF_EXTRA_PROPERTIES_MAPPER = new ObjectMapper();

    /**
     * 解析人员保存请求 {@code extraProperties} JSON 扩展字段。
     *
     * @param request 保存请求
     * @return 解析成功返回 JsonNode；空或非法 JSON 时 null
     */
    private JsonNode parseStaffExtraProperties(SaveStaffRequest request) {
        if (request == null || StringUtils.isEmpty(request.getExtraProperties())) {
            return null;
        }
        try {
            return STAFF_EXTRA_PROPERTIES_MAPPER.readTree(request.getExtraProperties().trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 从显式字段或 extraProperties JSON 读取人员文本扩展属性。
     *
     * @param request        保存请求
     * @param fieldName      JSON 字段名
     * @param explicitValue  请求体显式值（优先）
     * @return trim 后的值；均无则 null
     */
    private String resolveStaffTextField(SaveStaffRequest request, String fieldName, String explicitValue) {
        if (StringUtils.isNotEmpty(explicitValue)) {
            return explicitValue.trim();
        }
        JsonNode extra = parseStaffExtraProperties(request);
        if (extra == null || !extra.hasNonNull(fieldName)) {
            return null;
        }
        String value = extra.get(fieldName).asText();
        if (StringUtils.isEmpty(value)) {
            return null;
        }
        return value.trim();
    }

    /**
     * 解析医师职称代码：请求字段优先，否则从 extraProperties 读取整数。
     *
     * @param request 保存请求
     * @return 职称 code；缺失或非法时 null
     */
    private Integer resolveStaffDrProfttlCode(SaveStaffRequest request) {
        if (request.getDrProfttlCode() != null) {
            return request.getDrProfttlCode();
        }
        JsonNode extra = parseStaffExtraProperties(request);
        if (extra == null || !extra.hasNonNull("drProfttlCode")) {
            return null;
        }
        String raw = extra.get("drProfttlCode").asText();
        if (StringUtils.isEmpty(raw)) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 批量分配岗位后，按涉及医务人员刷新可登录科室列表。
     *
     * @param cmd 分配命令
     */
    private void syncLoginOrganizationsFromAssignCommand(AssignPractitionerRoleCommand cmd) {
        if (cmd == null) {
            return;
        }
        LinkedHashSet<Long> practitionerIds = new LinkedHashSet<>();
        for (AssignPractitionerRoleCommand.AssignPractitionerRoleItemCommand item : cmd.resolveItems()) {
            if (item != null && item.getPractitionerId() != null) {
                practitionerIds.add(item.getPractitionerId());
            }
        }
        for (Long practitionerId : practitionerIds) {
            userLoginOrganizationSyncService.syncForPractitioner(practitionerId);
        }
    }

    /**
     * 编辑/启停/停用岗位后，从 batch 命令项解析 assignmentId 并刷新对应人员可登录科室。
     *
     * @param cmd 含 assignmentId 的批量写命令
     */
    private void syncLoginOrganizationsFromAssignmentCommand(
            com.openhis.health.domain.share.command.BatchCommand<?> cmd) {
        if (cmd == null) {
            return;
        }
        LinkedHashSet<Long> practitionerIds = new LinkedHashSet<>();
        for (Object rawItem : cmd.resolveItems()) {
            Long assignmentId = this.extractAssignmentId(rawItem);
            if (assignmentId == null) {
                continue;
            }
            Long practitionerId = practitionerRoleQueryService.findPractitionerIdByAssignmentId(assignmentId);
            if (practitionerId != null) {
                practitionerIds.add(practitionerId);
            }
        }
        for (Long practitionerId : practitionerIds) {
            userLoginOrganizationSyncService.syncForPractitioner(practitionerId);
        }
    }

    /**
     * 库房/药房岗位：按 {@code locationId}（逗号分隔）全量替换同角色下的多条分配（不改表结构，一 location 一行）。
     */
    private void syncInventoryRoleLocations(
            Long operatorId, EditPractitionerRoleCommand.EditPractitionerRoleItemCommand item) {
        if (item.getAssignmentId() == null) {
            throw new ValidationException("岗位标识不能为空");
        }
        Long practitionerId = practitionerRoleQueryService.findPractitionerIdByAssignmentId(item.getAssignmentId());
        if (practitionerId == null) {
            throw new ValidationException("岗位分配不存在");
        }
        PractitionerRole anchor = null;
        for (PractitionerRole role : practitionerRoleQueryService.listAllRolesByPractitioner(practitionerId)) {
            if (item.getAssignmentId().equals(role.getId())) {
                anchor = role;
                break;
            }
        }
        if (anchor == null) {
            throw new ValidationException("岗位分配不存在");
        }
        ReplacePractitionerRolesByTypeCommand replaceCmd = new ReplacePractitionerRolesByTypeCommand();
        replaceCmd.setOperatorId(operatorId);
        replaceCmd.setPractitionerId(anchor.getPractitionerId());
        replaceCmd.setRoleId(anchor.getRoleId());
        List<AssignPractitionerRoleCommand.AssignPractitionerRoleItemCommand> assignItems = new ArrayList<>();
        String roleName;
        if (StringUtils.isNotEmpty(item.getName())) {
            roleName = item.getName().trim();
        } else {
            roleName = anchor.getName();
        }
        for (Long locationId : parseEditLocationIdCsv(item.getLocationId())) {
            AssignPractitionerRoleCommand.AssignPractitionerRoleItemCommand assignItem =
                    new AssignPractitionerRoleCommand.AssignPractitionerRoleItemCommand();
            assignItem.setPractitionerId(anchor.getPractitionerId());
            assignItem.setRoleId(anchor.getRoleId());
            assignItem.setLocationId(locationId);
            assignItem.setName(roleName);
            assignItems.add(assignItem);
        }
        replaceCmd.setItems(assignItems);
        practitionerRoleDomainService.replaceRolesByType(replaceCmd);
        positionUserRoleSyncService.afterReplaceBlock(anchor.getPractitionerId(), anchor.getRoleId());
    }

    /**
     * 分配命令入参归一化：解析并回填有效 platform roleId（兼容 roleCode 入参）。
     *
     * @param cmd 分配命令（就地修改 items.roleId）
     * @throws ValidationException 角色为空或无效
     */
    private void normalizeAssignCommandItems(AssignPractitionerRoleCommand cmd) {
        for (AssignPractitionerRoleCommand.AssignPractitionerRoleItemCommand item : cmd.resolveItems()) {
            if (item == null) {
                continue;
            }
            Long roleId = PositionRoleApiCompat.resolveRoleId(item.getRoleId(), item.getRoleCode(), whaleRoleResolver::findRoleIdByCode);
            if (roleId == null) {
                throw new ValidationException("平台角色不能为空或无效");
            }
            item.setRoleId(roleId);
        }
    }

    /**
     * 岗位退休/删除后同步平台用户角色绑定（按 assignment 反查 practitioner 与 role）。
     *
     * @param assignmentIds 已移除的岗位分配 id
     */
    private void syncUserRolesAfterRetire(List<Long> assignmentIds) {
        if (assignmentIds == null || assignmentIds.isEmpty()) {
            return;
        }
        for (Long assignmentId : assignmentIds) {
            if (assignmentId == null) {
                continue;
            }
            Long practitionerId = practitionerRoleQueryService.findPractitionerIdByAssignmentId(assignmentId);
            Long roleId = practitionerRoleQueryService.findRoleIdByAssignmentId(assignmentId);
            if (practitionerId != null && roleId != null) {
                positionUserRoleSyncService.onAssignmentRemoved(practitionerId, roleId);
            }
        }
    }

    /**
     * 判定岗位分配是否属于库房或药房平台角色（编辑时需按 location CSV 全量替换）。
     *
     * @param assignmentId 岗位分配主键
     * @return 库房/药房岗位为 true
     */
    private boolean isInventoryAssignment(Long assignmentId) {
        if (assignmentId == null) {
            return false;
        }
        Long practitionerId = practitionerRoleQueryService.findPractitionerIdByAssignmentId(assignmentId);
        if (practitionerId == null) {
            return false;
        }
        for (PractitionerRole role : practitionerRoleQueryService.listAllRolesByPractitioner(practitionerId)) {
            if (!assignmentId.equals(role.getId())) {
                continue;
            }
            Long roleId = role.getRoleId();
            return whaleRoleResolver.matchesPlatformRoleId(roleId, WhalePlatformRoleCodeConstants.ROLE_WAREHOUSE)
                    || whaleRoleResolver.matchesPlatformRoleId(roleId, WhalePlatformRoleCodeConstants.ROLE_PHARMACIST);
        }
        return false;
    }

    /**
     * 解析编辑岗位表单中逗号分隔的 locationId 字符串为 Long 列表。
     *
     * @param locationId 逗号分隔位置 id（可空）
     * @return 解析结果；空串返回空列表
     * @throws ValidationException 含非数字片段
     */
    private List<Long> parseEditLocationIdCsv(String locationId) {
        List<Long> result = new ArrayList<>();
        if (locationId == null || locationId.isBlank()) {
            return result;
        }
        for (String part : locationId.split(",")) {
            if (part == null || part.isBlank()) {
                continue;
            }
            try {
                result.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ex) {
                throw new ValidationException("位置标识格式无效");
            }
        }
        return result;
    }

    /**
     * 校验并归一化人员性别枚举 code（AdministrativeGender）。
     *
     * @param genderEnum 请求性别 code
     * @return 大写 trim 后的合法 code
     * @throws ValidationException 空或非法枚举
     */
    private String normalizeGenderEnum(String genderEnum) {
        if (StringUtils.isEmpty(genderEnum)) {
            throw new ValidationException("人员性别不能为空");
        }
        String normalized = genderEnum.trim().toUpperCase(Locale.ROOT);
        if (EnumUtil.fromCode(AdministrativeGender.class, normalized) == null) {
            throw new ValidationException("人员性别无效");
        }
        return normalized;
    }

    /**
     * 从岗位组织/位置行收集 orgId 列表（过滤 null）。
     *
     * @param rows 岗位分配行
     * @return orgId 列表；无有效行时空列表
     */
    private static List<Long> collectOrganizationIds(List<StaffOrgLocationItemDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (StaffOrgLocationItemDto row : rows) {
            if (row != null && row.getOrgId() != null) {
                ids.add(row.getOrgId());
            }
        }
        return ids;
    }

    /**
     * 从岗位组织/位置行收集 locationId 列表（过滤 null）。
     *
     * @param rows 岗位分配行
     * @return locationId 列表；无有效行时空列表
     */
    private static List<Long> collectLocationIds(List<StaffOrgLocationItemDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (StaffOrgLocationItemDto row : rows) {
            if (row != null && row.getLocationId() != null) {
                ids.add(row.getLocationId());
            }
        }
        return ids;
    }

    /**
     * 从各类岗位 batch 命令项中提取 assignmentId（编辑/退休/启停/停用）。
     *
     * @param item 命令项实例
     * @return assignmentId；无法识别类型时 null
     */
    private Long extractAssignmentId(Object item) {
        if (item instanceof EditPractitionerRoleCommand.EditPractitionerRoleItemCommand editItem) {
            return editItem.getAssignmentId();
        }
        if (item instanceof RetirePractitionerRoleCommand.RetirePractitionerRoleItemCommand retireItem) {
            return retireItem.getAssignmentId();
        }
        if (item instanceof ActivatePractitionerRoleCommand.ActivatePractitionerRoleItemCommand activateItem) {
            return activateItem.getAssignmentId();
        }
        if (item instanceof DeactivatePractitionerRoleCommand.DeactivatePractitionerRoleItemCommand deactivateItem) {
            return deactivateItem.getAssignmentId();
        }
        return null;
    }
}
