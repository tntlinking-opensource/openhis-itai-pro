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

package com.openhis.health.application.basic.position.service;

import com.openhis.health.application.basic.position.dto.*;
import com.openhis.health.application.basic.position.mapper.PositionMapper;
import com.openhis.health.application.basic.position.support.PositionPractitionerRoleWriteGate;
import com.openhis.health.application.basic.position.support.PositionRoleApiCompat;
import com.openhis.health.module.common.validator.CurrentOperatorResolver;
import com.openhis.health.module.common.validator.WhaleRoleLookup;
import com.openhis.health.module.common.validator.StaffManagedScopeWriteGate;
import com.openhis.health.module.querysupport.administration.PractitionerRoleScopeLoader;
import com.openhis.health.bridge.shared.identity.UserLoginOrganizationSyncService;
import com.openhis.health.bridge.shared.organization.OrganizationUnitLinkSyncService;
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
import org.apache.commons.lang3.StringUtils;
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
    private final WhaleRoleLookup whaleRoleLookup;
    private final PositionUserRoleSyncService positionUserRoleSyncService;
    private final PositionPractitionerRoleWriteGate positionPractitionerRoleWriteGate;
    private final OrganizationQueryService organizationQueryService;
    private final PlatformTenantAccessPolicy platformTenantAccessPolicy;

    /**
     * 岗位工作台左侧：分页查询系统用户，并装配系统角色名与绑定医务人员主档（若存在）。
     */
    @RequirePermission("Navigation.PositionManagement")
    public PagedResult<PositionUserWorkbenchRowDto> pageUsersForWorkbench(UserQueryRequest request) {
        if (request == null) {
            request = new UserQueryRequest();
        }
        request.setIsActive(Boolean.TRUE);
        PagedResult<UserDto> userPage = userAppService.getList(request);
        List<PositionUserWorkbenchRowDto> rows = new ArrayList<>();
        if (userPage.getContent() == null) {
            return PagedResult.of(rows, userPage.getTotalCount(), userPage.getPageNo(), userPage.getPageSize());
        }
        for (UserDto u : userPage.getContent()) {
            List<RoleDto> roles = userAppService.getRoles(u.getId());
            Practitioner practitioner = practitionerQueryService.findByUserId(u.getId());
            rows.add(positionMapper.toWorkbenchRow(u, roles, practitioner));
        }
        return PagedResult.of(rows, userPage.getTotalCount(), userPage.getPageNo(), userPage.getPageSize());
    }

    /**
     * 岗位分配可选内置角色（医师/护士/药师/库管/财务等），供岗位维护页下拉使用。
     *
     * @return 内置业务角色选项（含岗位范围声明）
     */
    @RequirePermission("Navigation.PositionManagement")
    public List<RoleOptionDto> listAssignmentRoleOptions() {
        return builtInBusinessRoleOptionLoader.loadAssignmentRoleOptions();
    }

    /**
     * 列出指定医务人员的全部岗位分配行。
     */
    @RequirePermission("Navigation.PositionManagement")
    public List<PractitionerAssignmentRowDto> listPractitionerAssignments(Long practitionerId) {
        if (practitionerId == null) {
            throw new ValidationException("医务人员标识不能为空");
        }
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
        return out;
    }

    /**
     * 当前登录人可维护的岗位人员信息块（StaffScope 并集）。
     * <p>仅依据操作者自身角色声明返回，不暴露他人数据，故仅需登录态。</p>
     */
    public StaffEditableScopesDto getEditableScopesForCurrentUser() {
        Long operatorId = currentOperatorResolver.requireOperatorId(
                ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT, "请先登录");
        StaffEditableScopesDto dto = new StaffEditableScopesDto();
        dto.setEditableScopes(new ArrayList<>(resolveEditableScopes(operatorId)));
        return dto;
    }

    /**
     * 按系统用户主键加载岗位人员维护详情（用户管理维护入口，仅需登录态）。
     */
    public StaffDetailDto getStaffDetail(Long userId) {
        if (userId == null) {
            throw new ValidationException("用户标识不能为空");
        }
        UserDto user = userAppService.getById(buildIdRequest(userId));
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
            detail.setGenderEnum(practitioner.getGenderEnum());
            detail.setStatusEnum(practitioner.getStatusEnum());
            detail.setPrimaryOrgId(practitioner.getOrgId());
            fillAssignmentBlocks(detail, practitioner.getId());
        }
        return detail;
    }

    /**
     * 保存岗位人员：新建或更新用户、平台角色、医务人员与六类岗位分配。
     */
    public StaffDetailDto saveStaff(SaveStaffRequest request) {
        if (request == null) {
            throw new ValidationException("请求不能为空");
        }
        Long operatorId = currentOperatorResolver.requireOperatorId(
                ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT, "请先登录后再维护人员");
        Set<String> editableScopes = new LinkedHashSet<>(resolveEditableScopes(operatorId));

        Long userId = resolvePlatformUserId(request);
        UserDto user;
        if (userId == null) {
            if (StringUtils.isBlank(request.getUserName())) {
                throw new ValidationException("账号不能为空");
            }
            if (StringUtils.isBlank(request.getPassword())) {
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
            editCmd.setItem(editItem);
            practitionerDomainService.editPractitioner(editCmd);
            if (Boolean.TRUE.equals(request.getSyncPrimaryOrgId()) && request.getPrimaryOrgId() != null) {
                syncPrimaryOrgId(practitionerId, operatorId, practitioner.getOrgId(), request.getPrimaryOrgId());
            }
        }

        String practitionerName = resolvePractitionerName(request, user);
        Set<String> roleStaffScopes = resolveStaffScopesForRoleIds(request.getRoleIds());
        replaceStaffScopeBlocks(practitionerId, operatorId, request, practitionerName, editableScopes, roleStaffScopes);

        organizationUnitAppService.syncOrganizationUnitsForUser(userId,
                organizationUnitLinkSyncService.resolveWhaleOrganizationUnitIds(
                        collectLoginOrganizationIds(practitionerId)));

        // 平台登录角色须在岗位块 replace 之后写入，否则 afterReplaceBlock 会按空岗位撤销 whale_user_roles。
        if (request.getRoleIds() != null) {
            AssignRolesRequest assignRolesRequest = new AssignRolesRequest();
            assignRolesRequest.setRoleIds(request.getRoleIds());
            userAppService.assignRoles(userId, assignRolesRequest);
        }

        return getStaffDetail(userId);
    }

    @RequirePermission("Navigation.PositionManagement")
    public List<OptionDto> listLocationOptionsByOrganization(Long organizationId) {
        return positionMapper.toSortedLocationOptions(locationQueryService.listActiveLocationsByOrganization(organizationId));
    }

    @RequirePermission("Navigation.PositionManagement")
    public OptionDto getLocationOptionById(Long locationId) {
        return locationQueryService
                .findOptionalById(locationId)
                .map(loc -> positionMapper.toLocationOption(loc, 0L))
                .orElse(null);
    }

    @RequirePermission("Navigation.PositionManagement")
    public Long assignPractitionerRole(AssignPractitionerRoleCommand cmd) {
        cmd.setOperatorId(currentOperatorResolver.requireOperatorId(
                ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_ARGUMENT, "请先登录后再操作岗位"));
        normalizeAssignCommandItems(cmd);
        positionPractitionerRoleWriteGate.assertAssignItemsValid(cmd.resolveItems());
        Long assignmentId = practitionerRoleDomainService.assignRole(cmd);
        for (AssignPractitionerRoleCommand.AssignPractitionerRoleItemCommand item : cmd.resolveItems()) {
            if (item != null && item.getPractitionerId() != null && item.getRoleId() != null) {
                positionUserRoleSyncService.onAssignmentAdded(item.getPractitionerId(), item.getRoleId());
            }
        }
        this.syncLoginOrganizationsFromAssignCommand(cmd);
        return assignmentId;
    }

    @RequirePermission("Navigation.PositionManagement")
    public void editPractitionerRole(EditPractitionerRoleCommand cmd) {
        cmd.setOperatorId(currentOperatorResolver.requireOperatorId(
                ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_ARGUMENT, "请先登录后再操作岗位"));
        List<EditPractitionerRoleCommand.EditPractitionerRoleItemCommand> resolved = cmd.resolveItems();
        List<EditPractitionerRoleCommand.EditPractitionerRoleItemCommand> normalItems = new ArrayList<>();
        for (EditPractitionerRoleCommand.EditPractitionerRoleItemCommand item : resolved) {
            if (item != null && isInventoryAssignment(item.getAssignmentId())) {
                syncInventoryRoleLocations(cmd.getOperatorId(), item);
            } else if (item != null) {
                normalItems.add(item);
            }
        }
        if (!normalItems.isEmpty()) {
            EditPractitionerRoleCommand normalCmd = new EditPractitionerRoleCommand();
            normalCmd.setOperatorId(cmd.getOperatorId());
            normalCmd.setItems(normalItems);
            practitionerRoleDomainService.editRole(normalCmd);
            syncAssignmentServicesFromEditCommand(normalCmd);
        }
        this.syncLoginOrganizationsFromAssignmentCommand(cmd);
    }

    @RequirePermission("Navigation.PositionManagement")
    public void retirePractitionerRole(RetirePractitionerRoleCommand cmd) {
        cmd.setOperatorId(currentOperatorResolver.requireOperatorId(
                ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_ARGUMENT, "请先登录后再操作岗位"));
        List<RetirePractitionerRoleCommand.RetirePractitionerRoleItemCommand> items = cmd.resolveItems();
        List<Long> assignmentIds = new ArrayList<>();
        for (RetirePractitionerRoleCommand.RetirePractitionerRoleItemCommand item : items) {
            if (item != null && item.getAssignmentId() != null) {
                assignmentIds.add(item.getAssignmentId());
            }
        }
        practitionerRoleDomainService.retireRole(cmd);
        syncUserRolesAfterRetire(assignmentIds);
        this.syncLoginOrganizationsFromAssignmentCommand(cmd);
    }

    @RequirePermission("Navigation.PositionManagement")
    public void activatePractitionerRole(ActivatePractitionerRoleCommand cmd) {
        cmd.setOperatorId(currentOperatorResolver.requireOperatorId(
                ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_ARGUMENT, "请先登录后再操作岗位"));
        practitionerRoleDomainService.activateRole(cmd);
        this.syncLoginOrganizationsFromAssignmentCommand(cmd);
    }

    @RequirePermission("Navigation.PositionManagement")
    public void deactivatePractitionerRole(DeactivatePractitionerRoleCommand cmd) {
        cmd.setOperatorId(currentOperatorResolver.requireOperatorId(
                ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_ARGUMENT, "请先登录后再操作岗位"));
        practitionerRoleDomainService.deactivateRole(cmd);
        this.syncLoginOrganizationsFromAssignmentCommand(cmd);
    }

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

    private String formatServiceNames(List<Long> serviceIds, Map<Long, HealthcareService> serviceMap) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            return "";
        }
        return serviceIds.stream()
                .map(id -> {
                    HealthcareService service = serviceMap.get(id);
                    if (service == null || StringUtils.isBlank(service.getName())) {
                        return id == null ? "" : String.valueOf(id);
                    }
                    return service.getName().trim();
                })
                .filter(name -> !name.isBlank())
                .collect(Collectors.joining("、"));
    }

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

    private static Set<String> allStaffScopeCodes() {
        Set<String> scopes = new LinkedHashSet<>();
        scopes.add(RoleStaffScopeConstants.DOCTOR_VISIT_ORG);
        scopes.add(RoleStaffScopeConstants.WAREHOUSE_LOCATION);
        scopes.add(RoleStaffScopeConstants.PHARMACY_LOCATION);
        scopes.add(RoleStaffScopeConstants.NURSE_ORG);
        scopes.add(RoleStaffScopeConstants.NURSE_WARD_LOCATION);
        return scopes;
    }

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
            if (matchesPlatformRole(roleId, WhalePlatformRoleCodeConstants.ROLE_DOCTOR)) {
                doctorVisit.add(row);
                if (role.getId() != null) {
                    doctorAssignmentIds.add(role.getId());
                }
            } else if (matchesPlatformRole(roleId, WhalePlatformRoleCodeConstants.ROLE_WAREHOUSE)) {
                warehouse.add(row);
            } else if (matchesPlatformRole(roleId, WhalePlatformRoleCodeConstants.ROLE_PHARMACIST)) {
                pharmacy.add(row);
            } else if (matchesPlatformRole(roleId, WhalePlatformRoleCodeConstants.ROLE_NURSE)) {
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
            List<StaffOrgLocationItemDto> nurseItems = request.getNurseWardLocationList() != null
                    ? new ArrayList<>(request.getNurseWardLocationList())
                    : List.of();
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

    private static boolean shouldReplaceStaffScope(
            String staffScope, Set<String> editableScopes, Set<String> roleStaffScopes) {
        return editableScopes.contains(staffScope)
                && roleStaffScopes.contains(staffScope)
                && isBuiltinStaffScope(staffScope);
    }

    private static boolean isBuiltinStaffScope(String staffScope) {
        return RoleStaffScopeConstants.DOCTOR_VISIT_ORG.equals(staffScope)
                || RoleStaffScopeConstants.WAREHOUSE_LOCATION.equals(staffScope)
                || RoleStaffScopeConstants.PHARMACY_LOCATION.equals(staffScope)
                || RoleStaffScopeConstants.NURSE_ORG.equals(staffScope)
                || RoleStaffScopeConstants.NURSE_WARD_LOCATION.equals(staffScope);
    }

    private Set<String> resolveStaffScopesForRoleIds(List<Long> roleIds) {
        Set<String> scopes = new LinkedHashSet<>();
        if (roleIds == null || roleIds.isEmpty()) {
            return scopes;
        }
        for (Long roleId : roleIds) {
            if (roleId == null) {
                continue;
            }
            String code = whaleRoleLookup.findRoleCodeById(roleId);
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

    private void replaceBlock(
            Long practitionerId,
            Long operatorId,
            String staffScope,
            List<StaffOrgLocationItemDto> rows,
            String practitionerName,
            boolean orgMode,
            List<Long> serviceIds) {
        Long roleId = PositionRoleApiCompat.defaultRoleIdForStaffScope(staffScope, whaleRoleLookup);
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

    private String resolvePractitionerName(SaveStaffRequest request, UserDto user) {
        if (StringUtils.isNotBlank(request.getName())) {
            return request.getName().trim();
        }
        if (user != null && StringUtils.isNotBlank(user.getName())) {
            return user.getName().trim();
        }
        return request.getUserName();
    }

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
            this.syncLoginOrganizationsForPractitioner(practitionerId);
        }
    }

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
            this.syncLoginOrganizationsForPractitioner(practitionerId);
        }
    }

    private void syncLoginOrganizationsForPractitioner(Long practitionerId) {
        userLoginOrganizationSyncService.syncForPractitioner(practitionerId);
    }

    private IdRequest buildIdRequest(Long id) {
        IdRequest request = new IdRequest();
        request.setId(id);
        return request;
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
        String roleName = StringUtils.isNotBlank(item.getName()) ? item.getName().trim() : anchor.getName();
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

    private void normalizeAssignCommandItems(AssignPractitionerRoleCommand cmd) {
        for (AssignPractitionerRoleCommand.AssignPractitionerRoleItemCommand item : cmd.resolveItems()) {
            if (item == null) {
                continue;
            }
            Long roleId = PositionRoleApiCompat.resolveRoleId(item.getRoleId(), item.getRoleCode(), whaleRoleLookup);
            if (roleId == null) {
                throw new ValidationException("平台角色不能为空或无效");
            }
            item.setRoleId(roleId);
        }
    }

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
            return matchesPlatformRole(roleId, WhalePlatformRoleCodeConstants.ROLE_WAREHOUSE)
                    || matchesPlatformRole(roleId, WhalePlatformRoleCodeConstants.ROLE_PHARMACIST);
        }
        return false;
    }

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

    private boolean matchesPlatformRole(Long roleId, String whaleRoleCode) {
        return whaleRoleLookup.matchesPlatformRoleId(roleId, whaleRoleCode);
    }

    private String normalizeGenderEnum(String genderEnum) {
        if (StringUtils.isBlank(genderEnum)) {
            throw new ValidationException("人员性别不能为空");
        }
        String normalized = genderEnum.trim().toUpperCase(Locale.ROOT);
        if (EnumUtil.fromCode(AdministrativeGender.class, normalized) == null) {
            throw new ValidationException("人员性别无效");
        }
        return normalized;
    }

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
