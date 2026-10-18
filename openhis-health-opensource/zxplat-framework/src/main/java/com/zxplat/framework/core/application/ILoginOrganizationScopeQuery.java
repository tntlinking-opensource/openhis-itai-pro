/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.application;

import com.zxplat.framework.core.application.dto.LoginOrganizationOption;

import java.util.List;
import java.util.Optional;

/**
 * 登录选医院/科室时的 HIS 组织树解析（health 模块实现）。
 */
public interface ILoginOrganizationScopeQuery {

    /**
     * 当前租户下启用的医院（{@code type_enum=HOSPITAL}）。
     */
    List<LoginOrganizationOption> listActiveHospitalOrganizations();

    /**
     * 解析业务组织所属医院根节点（{@code adm_organization.id}）。
     */
    Optional<Long> resolveHospitalOrganizationId(Long businessOrganizationId);

    /**
     * 判断科室/组织是否隶属于指定医院。
     */
    boolean belongsToHospital(Long businessOrganizationId, Long hospitalOrganizationId);

    /**
     * 加载组织展示信息。
     */
    Optional<LoginOrganizationOption> loadOrganizationOption(Long businessOrganizationId);
}
