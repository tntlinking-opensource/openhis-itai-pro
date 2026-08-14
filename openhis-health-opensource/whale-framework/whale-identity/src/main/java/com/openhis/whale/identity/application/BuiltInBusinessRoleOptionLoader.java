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
     * 解析角色人员维护块 StaffScope：优先读声明（含自定义角色），无声明时回退内置 code 模板。
     * <p>供岗位/用户维护编排调用，避免走带 REST 权限的 {@code RoleAppService.getStaffScopes}。</p>
     *
     * @param roleId 平台角色主键
     * @return StaffScope code 列表（可能为空）
     */
    public List<String> resolveStaffScopesForRoleId(Long roleId) {
        if (roleId == null) {
            return List.of();
        }
        // 业务：声明优先（自定义角色在角色管理勾选的管理药房/库房等）
        List<String> fromClaims = readClaimValues(roleId, RoleStaffScopeConstants.CLAIM_TYPE);
        if (!fromClaims.isEmpty()) {
            return fromClaims;
        }
        // 业务续：内置角色种子缺失时按 code 模板兜底
        Role role = roleRepository.findById(roleId).orElse(null);
        if (role == null || role.getCode() == null || role.getCode().isBlank()) {
            return List.of();
        }
        return defaultStaffScopesForCode(role.getCode());
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
        if ("ROLE_PHARMACIST".equals(normalized)) {
            return List.of(RoleAssignmentScopeConstants.PHARMACY);
        }
        if ("ROLE_WAREHOUSE".equals(normalized)) {
            return List.of(RoleAssignmentScopeConstants.WAREHOUSE);
        }
        if ("ROLE_ACCOUNTANT".equals(normalized)) {
            return List.of(RoleAssignmentScopeConstants.ORG);
        }
        if ("ROLE_FINANCE_APPROVER".equals(normalized)) {
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
            return List.of(RoleStaffScopeConstants.NURSE_WARD_LOCATION);
        }
        if ("ROLE_PHARMACIST".equals(normalized)) {
            return List.of(RoleStaffScopeConstants.PHARMACY_LOCATION);
        }
        if ("ROLE_WAREHOUSE".equals(normalized)) {
            // 库管只管库房；管理药房不是药师岗，不得因库管声明 PHARMACY_LOCATION 而写入 ROLE_PHARMACIST。
            return List.of(RoleStaffScopeConstants.WAREHOUSE_LOCATION);
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
