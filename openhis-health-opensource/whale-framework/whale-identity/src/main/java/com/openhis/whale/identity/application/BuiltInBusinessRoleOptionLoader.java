/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.application;

import com.openhis.whale.identity.application.dto.RoleOptionDto;
import com.openhis.whale.identity.domain.Role;
import com.openhis.whale.identity.domain.RoleClaim;
import com.openhis.whale.identity.infrastructure.repository.RoleClaimRepository;
import com.openhis.whale.identity.infrastructure.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 岗位业务角色辅助：内置角色下拉装配，以及按角色声明解析人员维护块 StaffScope
 * （含自定义角色；非 AppService，避免 REST 权限与岗位模块耦合）。
 */
@Component
@RequiredArgsConstructor
public class BuiltInBusinessRoleOptionLoader {

    private final RoleRepository roleRepository;
    private final RoleClaimRepository roleClaimRepository;

    /**
     * 加载岗位分配可选内置角色（不含系统管理员）。
     *
     * @return 内置业务角色选项
     */
    public List<RoleOptionDto> loadAssignmentRoleOptions() {
        List<RoleOptionDto> result = new ArrayList<>();
        for (Role role : roleRepository.findByIsStaticTrueAndIsDeletedFalseOrderByIdAsc()) {
            if (RolePlatformConstants.CODE_ADMIN.equalsIgnoreCase(role.getCode())) {
                continue;
            }
            RoleOptionDto option = new RoleOptionDto();
            option.setId(role.getId());
            option.setDisplayName(role.getDisplayName());
            option.setBuiltIn(Boolean.TRUE);
            option.setCode(role.getCode());
            option.setAssignmentScopes(resolveAssignmentScopes(role));
            result.add(option);
        }
        return result;
    }

    /**
     * 内置角色岗位范围以 code 模板为准（不在界面手工维护 AssignmentScope）。
     */
    private List<String> resolveAssignmentScopes(Role role) {
        List<String> fromTemplate = defaultAssignmentScopesForCode(role.getCode());
        if (!fromTemplate.isEmpty()) {
            return fromTemplate;
        }
        return readClaimValues(role.getId(), RoleAssignmentScopeConstants.CLAIM_TYPE);
    }

    /**
     * 解析角色人员维护块 StaffScope。
     * <p>内置业务角色以 code 模板为准（与 AssignmentScope 同构），避免库内旧声明
     * （如技师误挂 {@code DOCTOR_VISIT_ORG}、护士仍挂已废弃 {@code NURSE_ORG}）导致人员维护显示「出诊科室」而非「工作科室」。
     * 自定义角色仍读声明。</p>
     *
     * @param roleId 平台角色主键
     * @return StaffScope code 列表（可能为空）
     */
    public List<String> resolveStaffScopesForRoleId(Long roleId) {
        if (roleId == null) {
            return List.of();
        }
        Role role = roleRepository.findById(roleId).orElse(null);
        if (role == null) {
            return List.of();
        }
        // 内置业务角色：模板优先，与界面「系统固定」口径一致
        List<String> fromTemplate = defaultStaffScopesForCode(role.getCode());
        if (!fromTemplate.isEmpty()) {
            return fromTemplate;
        }
        // 自定义角色：读角色管理勾选的声明
        return readClaimValues(role.getId(), RoleStaffScopeConstants.CLAIM_TYPE);
    }

    private List<String> defaultAssignmentScopesForCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return List.of();
        }
        String normalized = roleCode.trim().toUpperCase(Locale.ROOT);
        if ("ROLE_DOCTOR".equals(normalized)) {
            return List.of(RoleAssignmentScopeConstants.ORG);
        }
        if ("ROLE_NURSE".equals(normalized)) {
            return List.of(RoleAssignmentScopeConstants.ORG, RoleAssignmentScopeConstants.WARD);
        }
        if ("ROLE_MEDICAL_TECH".equals(normalized)) {
            return List.of(RoleAssignmentScopeConstants.ORG);
        }
        if ("ROLE_PHARMACIST".equals(normalized)) {
            return List.of(RoleAssignmentScopeConstants.PHARMACY);
        }
        if ("ROLE_WAREHOUSE".equals(normalized)) {
            // 库管：药库 + 药房（与种子 claim、前端 BUILTIN_ROLE_STAFF_SCOPES 一致）
            return List.of(
                RoleAssignmentScopeConstants.WAREHOUSE,
                RoleAssignmentScopeConstants.PHARMACY);
        }
        if ("ROLE_ACCOUNTANT".equals(normalized)) {
            return List.of(RoleAssignmentScopeConstants.ORG);
        }
        // 款员与财务一样只挂科室，不维护库房/药房/病区
        if ("ROLE_CASHIER".equals(normalized)) {
            return List.of(RoleAssignmentScopeConstants.ORG);
        }
        if ("ROLE_MRMS_ADMIN".equals(normalized)) {
            return List.of(RoleAssignmentScopeConstants.ORG);
        }
        return List.of();
    }

    /**
     * 内置业务角色默认人员维护块（与历史 PositionAppService 硬编码模板一致）。
     */
    private List<String> defaultStaffScopesForCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return List.of();
        }
        String normalized = roleCode.trim().toUpperCase(Locale.ROOT);
        if ("ROLE_DOCTOR".equals(normalized)) {
            return List.of(RoleStaffScopeConstants.DOCTOR_VISIT_ORG);
        }
        if ("ROLE_NURSE".equals(normalized)) {
            return List.of(
                RoleStaffScopeConstants.NURSE_WARD_LOCATION,
                RoleStaffScopeConstants.WORK_ORG);
        }
        if ("ROLE_MEDICAL_TECH".equals(normalized)) {
            return List.of(RoleStaffScopeConstants.WORK_ORG);
        }
        if ("ROLE_PHARMACIST".equals(normalized)) {
            return List.of(RoleStaffScopeConstants.PHARMACY_LOCATION);
        }
        if ("ROLE_WAREHOUSE".equals(normalized)) {
            // 库管：管理库房 + 管理药房（与种子 claim、前端模板一致；不等于药师岗）
            return List.of(
                RoleStaffScopeConstants.WAREHOUSE_LOCATION,
                RoleStaffScopeConstants.PHARMACY_LOCATION);
        }
        return List.of();
    }

    private List<String> readClaimValues(Long roleId, String claimType) {
        List<String> values = new ArrayList<>();
        if (roleId == null || claimType == null) {
            return values;
        }
        List<RoleClaim> claims = roleClaimRepository.findByRoleId(roleId);
        for (RoleClaim claim : claims) {
            if (claim == null || claim.getClaimType() == null || claim.getClaimValue() == null) {
                continue;
            }
            if (!claimType.equals(claim.getClaimType())) {
                continue;
            }
            values.add(claim.getClaimValue());
        }
        return values;
    }
}
