/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.config;

import com.openhis.health.module.common.validator.FinanceApprovalRoleGate;
import com.openhis.whale.core.application.IApplicationConfigurationAppService;
import com.openhis.whale.core.application.dto.ApplicationConfigurationDto;
import com.openhis.whale.core.application.dto.CurrentUserDto;
import com.openhis.whale.web.application.ApplicationConfigurationAppService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * HIS 应用配置装饰：在框架 {@link ApplicationConfigurationAppService} 之上补充 {@code canFinanceApprove} 等健康域字段。
 */
@Service
@Primary
public class HealthApplicationConfigurationAppService implements IApplicationConfigurationAppService {

    private final ApplicationConfigurationAppService delegate;
    private final FinanceApprovalRoleGate financeApprovalRoleGate;

    /**
     * @param delegate 框架默认配置服务（显式注入，避免与 {@code @Primary} 自引用）
     * @param financeApprovalRoleGate 财务审批岗位鉴权
     */
    public HealthApplicationConfigurationAppService(
        ApplicationConfigurationAppService delegate,
        FinanceApprovalRoleGate financeApprovalRoleGate) {
        this.delegate = delegate;
        this.financeApprovalRoleGate = financeApprovalRoleGate;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ApplicationConfigurationDto get() {
        ApplicationConfigurationDto result = this.delegate.get();
        this.enrichCurrentUser(result);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ApplicationConfigurationDto get(Long userId, Long tenantId) {
        ApplicationConfigurationDto result = this.delegate.get(userId, tenantId);
        this.enrichCurrentUser(result);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void applyImpersonatorFields(
        CurrentUserDto userDto,
        Long impersonatorUserId,
        String impersonatorUserName,
        Long impersonatorTenantId) {
        this.delegate.applyImpersonatorFields(userDto, impersonatorUserId, impersonatorUserName, impersonatorTenantId);
    }

    private void enrichCurrentUser(ApplicationConfigurationDto configuration) {
        if (configuration == null || configuration.getCurrentUser() == null) {
            return;
        }
        CurrentUserDto currentUser = configuration.getCurrentUser();
        if (!Boolean.TRUE.equals(currentUser.getIsAuthenticated())) {
            return;
        }
        currentUser.setCanFinanceApprove(this.financeApprovalRoleGate.hasFinanceApprovalRole());
    }
}
