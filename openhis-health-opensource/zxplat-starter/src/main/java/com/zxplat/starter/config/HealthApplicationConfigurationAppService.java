/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.starter.config;

import com.zxplat.domain.infrastructure.platform.authorization.FinanceApprovalRoleGate;
import com.zxplat.domain.infrastructure.platform.authorization.MrmsAdminRoleGate;
import com.zxplat.framework.core.application.IApplicationConfigurationAppService;
import com.zxplat.framework.core.application.dto.ApplicationConfigurationDto;
import com.zxplat.framework.core.application.dto.CurrentUserDto;
import com.zxplat.framework.web.application.ApplicationConfigurationAppService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * HIS 应用配置装饰：在框架 {@link ApplicationConfigurationAppService} 之上补充健康域登录能力字段。
 *
 * <p>当前补充：{@code canFinanceApprove}、{@code canMrmsAdmin}。</p>
 */
@Service
@Primary
public class HealthApplicationConfigurationAppService implements IApplicationConfigurationAppService {

    private final ApplicationConfigurationAppService delegate;
    private final FinanceApprovalRoleGate financeApprovalRoleGate;
    private final MrmsAdminRoleGate mrmsAdminRoleGate;

    /**
     * @param delegate                框架默认配置服务（显式注入，避免与 {@code @Primary} 自引用）
     * @param financeApprovalRoleGate 财务审批登录角色鉴权
     * @param mrmsAdminRoleGate       病案管理员登录角色鉴权
     */
    public HealthApplicationConfigurationAppService(
        ApplicationConfigurationAppService delegate,
        FinanceApprovalRoleGate financeApprovalRoleGate,
        MrmsAdminRoleGate mrmsAdminRoleGate) {
        this.delegate = delegate;
        this.financeApprovalRoleGate = financeApprovalRoleGate;
        this.mrmsAdminRoleGate = mrmsAdminRoleGate;
    }

    /**
     * 获取当前登录上下文下的应用配置，在框架实现之上补充健康域用户字段。
     *
     * @return 应用配置 DTO
     */
    @Override
    public ApplicationConfigurationDto get() {
        // Pre：委托框架组装登录上下文、菜单与租户信息
        ApplicationConfigurationDto result = this.delegate.get();
        // Asm：补充健康域扩展字段（财务审批 / 病案管理员）
        this.enrichCurrentUser(result);
        return result;
    }

    /**
     * 按指定用户与租户获取应用配置（用于租户切换/模拟登录），并补充健康域用户字段。
     *
     * @param userId   用户主键
     * @param tenantId 租户主键，可为 null 时由框架解析用户主租户
     * @return 应用配置 DTO
     */
    @Override
    public ApplicationConfigurationDto get(Long userId, Long tenantId) {
        // Pre：按指定用户/租户委托框架解析配置（租户切换、模拟登录场景）
        ApplicationConfigurationDto result = this.delegate.get(userId, tenantId);
        // Asm：补充健康域扩展字段
        this.enrichCurrentUser(result);
        return result;
    }

    /**
     * 回填模拟登录人展示字段，委托框架实现保持与 zxplat-framework web 一致。
     *
     * @param userDto                待回填的当前用户 DTO
     * @param impersonatorUserId     模拟人用户主键
     * @param impersonatorUserName   模拟人登录名
     * @param impersonatorTenantId   模拟人租户主键
     */
    @Override
    public void applyImpersonatorFields(
        CurrentUserDto userDto,
        Long impersonatorUserId,
        String impersonatorUserName,
        Long impersonatorTenantId) {
        this.delegate.applyImpersonatorFields(userDto, impersonatorUserId, impersonatorUserName, impersonatorTenantId);
    }

    /**
     * 在框架已组装的 {@link CurrentUserDto} 上补充健康域扩展字段。
     *
     * @param configuration 框架返回的应用配置，可为 null
     */
    private void enrichCurrentUser(ApplicationConfigurationDto configuration) {
        if (configuration == null || configuration.getCurrentUser() == null) {
            return;
        }
        CurrentUserDto currentUser = configuration.getCurrentUser();
        if (!Boolean.TRUE.equals(currentUser.getIsAuthenticated())) {
            return;
        }
        try {
            // Pre：按用户管理登录角色解析是否具备财务审批权限
            currentUser.setCanFinanceApprove(this.financeApprovalRoleGate.hasFinanceApprovalRole());
        } catch (RuntimeException ex) {
            // Gate 解析失败时降级为不可审批，避免启动/登录链路因角色数据异常中断
            currentUser.setCanFinanceApprove(false);
        }
        try {
            // Pre：按用户管理登录角色解析是否具备病案管理员权限（归档病例）
            currentUser.setCanMrmsAdmin(this.mrmsAdminRoleGate.hasMrmsAdminRole());
        } catch (RuntimeException ex) {
            // Gate 解析失败时降级为不可归档，避免登录链路中断
            currentUser.setCanMrmsAdmin(false);
        }
    }
}
