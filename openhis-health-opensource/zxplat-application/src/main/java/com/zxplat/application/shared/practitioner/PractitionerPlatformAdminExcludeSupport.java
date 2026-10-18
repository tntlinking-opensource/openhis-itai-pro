/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.practitioner;

import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.administration.practitioner.service.PractitionerQueryService;
import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.framework.identity.application.RolePlatformConstants;
import com.zxplat.framework.identity.application.UserAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 业务人员下拉剔除平台内置 admin（用户名 {@code admin}）。
 *
 * <p>医生/护士/收款/药房等<strong>操作选型</strong>下拉不展示运维账号；日结等「窗内实际经办人」场景由调用方
 * 关闭剔除或另行合并历史经办人。</p>
 */
@Component
@RequiredArgsConstructor
public class PractitionerPlatformAdminExcludeSupport {

    private final UserAppService userAppService;

    private final PractitionerQueryService practitionerQueryService;

    /** 缓存：平台 admin 绑定的医务人员主键；未绑定则为 {@code null}。 */
    private volatile Long cachedAdminPractitionerId;

    /** 是否已解析过 admin 医务人员主键（含「无绑定」结论）。 */
    private volatile boolean adminPractitionerResolved;

    /**
     * 解析平台 admin 账号绑定的医务人员主键（进程内缓存）。
     *
     * @return 医务人员主键；无 admin 用户或未绑定时 {@code null}
     */
    public Long findPlatformAdminPractitionerIdOrNull() {
        if (this.adminPractitionerResolved) {
            return this.cachedAdminPractitionerId;
        }
        synchronized (this) {
            if (this.adminPractitionerResolved) {
                return this.cachedAdminPractitionerId;
            }
            Long adminUserId = this.userAppService.findAdminUserIdOrNull();
            Long practitionerId = null;
            if (adminUserId != null) {
                Practitioner practitioner = this.practitionerQueryService.findByUserId(adminUserId);
                if (practitioner != null) {
                    practitionerId = practitioner.getId();
                }
            }
            this.cachedAdminPractitionerId = practitionerId;
            this.adminPractitionerResolved = true;
            return practitionerId;
        }
    }

    /**
     * 是否平台内置 admin 对应的医务人员（按绑定用户名或展示名）。
     *
     * @param practitioner 医务人员；可空
     * @return 是平台 admin 时 {@code true}
     */
    public boolean isPlatformAdminPractitioner(Practitioner practitioner) {
        if (practitioner == null) {
            return false;
        }
        Long adminPractitionerId = this.findPlatformAdminPractitionerIdOrNull();
        if (adminPractitionerId != null
            && practitioner.getId() != null
            && adminPractitionerId.equals(practitioner.getId())) {
            return true;
        }
        if (practitioner.getUserId() != null
            && this.userAppService.isAdminAccount(practitioner.getUserId())) {
            return true;
        }
        return this.isPlatformAdminDisplayName(practitioner.getName());
    }

    /**
     * 从列表剔除平台 admin（保持相对顺序）。
     *
     * @param practitioners 候选列表；可空
     * @return 剔除后的新列表；入参空时返回空列表
     */
    public List<Practitioner> excludePlatformAdmin(Collection<Practitioner> practitioners) {
        List<Practitioner> result = new ArrayList<>();
        if (practitioners == null || practitioners.isEmpty()) {
            return result;
        }
        for (Practitioner practitioner : practitioners) {
            if (practitioner == null || practitioner.getId() == null) {
                continue;
            }
            if (this.isPlatformAdminPractitioner(practitioner)) {
                continue;
            }
            result.add(practitioner);
        }
        return result;
    }

    /**
     * 是否平台内置运维展示名（忽略大小写、首尾空白）。
     *
     * @param name 医务人员姓名
     * @return 是 admin 展示名时 {@code true}
     */
    private boolean isPlatformAdminDisplayName(String name) {
        if (StringUtils.isEmpty(name)) {
            return false;
        }
        return RolePlatformConstants.ADMIN_USERNAME.equalsIgnoreCase(name.trim());
    }
}
