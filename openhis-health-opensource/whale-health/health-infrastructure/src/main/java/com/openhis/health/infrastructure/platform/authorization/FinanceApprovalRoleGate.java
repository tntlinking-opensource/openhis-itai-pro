/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.platform.authorization;

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
 * 财务审批鉴权：登录用户持有 {@link WhalePlatformRoleCodeConstants#ROLE_ACCOUNTANT} 时放行。
 *
 * <p>供应单白名单见 {@link SupplyType#requiresFinanceApproval(String)}（进货/退供应商/损益/盘点）；调价走 {@link #assertFinanceApprovalRole()}。</p>
 */
@Component
@RequiredArgsConstructor
public class FinanceApprovalRoleGate implements FinanceApprovalAuthorizer {

    private final ICurrentUser currentUser;
    private final UserAppService userAppService;

    /**
     * 当前登录用户是否具备财务审批权限（财务登录角色 {@code ROLE_ACCOUNTANT}）。
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
     * 断言当前用户具备财务审批权限（调价及财务审批页同意/驳回入口）。
     *
     * @throws BusinessException 无权限时
     */
    @Override
    public void assertFinanceApprovalRole() {
        if (this.hasFinanceApprovalRole()) {
            return;
        }
        throw new BusinessException(ErrorCodeConstants.FINANCE_APPROVAL_ROLE_REQUIRED, "无财务审批权限，无法审批或驳回");
    }

    /**
     * 用户是否拥有财务平台角色。
     *
     * @param userId 用户 id
     * @return true 表示可审批
     */
    private boolean hasFinanceApproverUserRole(Long userId) {
        List<RoleDto> roles = this.userAppService.getRoles(userId);
        // 遍历登录角色，只认财务；财务审批角色已废
        for (RoleDto role : roles) {
            // 空角色行不能当财务
            if (role == null || role.getCode() == null) {
                continue;
            }
            // 财务登录角色即可审批或驳回
            if (WhalePlatformRoleCodeConstants.ROLE_ACCOUNTANT.equalsIgnoreCase(role.getCode().trim())) {
                return true;
            }
        }
        return false;
    }
}
