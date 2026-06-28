/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 */

package com.openhis.health.module.common.validator;

import com.openhis.health.domain.share.authorization.FinanceApprovalAuthorizer;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import com.openhis.whale.identity.application.UserAppService;
import com.openhis.whale.identity.application.dto.RoleDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 财务审批鉴权：校验当前用户是否持有 {@link WhalePlatformRoleCodeConstants#ROLE_FINANCE_APPROVER} 系统角色。
 */
@Component
@RequiredArgsConstructor
public class FinanceApprovalRoleGate implements FinanceApprovalAuthorizer {

    private final ICurrentUser currentUser;
    private final UserAppService userAppService;

    /**
     * 当前登录用户是否具备财务审批权限（用户管理分配的 {@code ROLE_FINANCE_APPROVER}）。
     *
     * @return 可审批动账类单据时为 {@code true}
     */
    @Override
    public boolean hasFinanceApprovalRole() {
        if (!this.currentUser.isAuthenticated()) {
            return false;
        }
        Long userId = this.currentUser.getCurrentUserId();
        if (userId == null) {
            return false;
        }
        return this.hasFinanceApproverUserRole(userId);
    }

    /**
     * 动账类供应单审批前门禁：{@code supplyTypeCode} 不在动账白名单时直接返回。
     *
     * @param supplyTypeCode 持久化 {@code wkf_supply_request.type_enum}
     */
    @Override
    public void assertFinanceApprovalForSupplyType(String supplyTypeCode) {
        if (!SupplyType.requiresFinanceApproval(supplyTypeCode)) {
            return;
        }
        if (this.hasFinanceApprovalRole()) {
            return;
        }
        throw new BusinessException(ErrorCodeConstants.FINANCE_APPROVAL_ROLE_REQUIRED, "无财务审批权限，无法审批或驳回该单据");
    }

    /**
     * 断言当前用户具备财务审批权限（调价等独立域审批入口）。
     *
     * @throws BusinessException 无权限时
     */
    public void assertFinanceApprovalRole() {
        if (this.hasFinanceApprovalRole()) {
            return;
        }
        throw new BusinessException(ErrorCodeConstants.FINANCE_APPROVAL_ROLE_REQUIRED, "无财务审批权限，无法审批或驳回");
    }

    private boolean hasFinanceApproverUserRole(Long userId) {
        List<RoleDto> roles = this.userAppService.getRoles(userId);
        for (RoleDto role : roles) {
            if (role == null || role.getCode() == null) {
                continue;
            }
            if (WhalePlatformRoleCodeConstants.ROLE_FINANCE_APPROVER.equalsIgnoreCase(role.getCode().trim())) {
                return true;
            }
        }
        return false;
    }
}
