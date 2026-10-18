/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.application;

import java.util.List;
import java.util.Optional;

/**
 * 登录默认科室解析（health 模块实现）。
 *
 * <p>登录范围层级为「租户 → 医院 → 科室」。默认登录科室仅取 {@code adm_practitioner.org_id}
 * （须为科室节点）；医院由选院步骤单独处理，不得回落为默认登录科室。</p>
 */
public interface ILoginDefaultOrganizationResolver {

    /**
     * 解析用户默认登录科室（非医院节点）。
     *
     * @param userId                         平台用户主键
     * @param sessionHospitalOrganizationId  已选医院（HIS {@code adm_organization.id}）；可为 null，用于归属校验
     * @return 默认登录科室主键；未配置或配置为医院时 empty
     */
    Optional<Long> resolveDefaultLoginOrganizationId(Long userId, Long sessionHospitalOrganizationId);

    /**
     * 用户无岗位派生科室时，可用于登录选医院的医院主键列表。
     *
     * @param userId 平台用户主键
     * @return 医院 {@code adm_organization.id} 列表；无医务人员时不返回
     */
    List<Long> resolveFallbackHospitalOrganizationIds(Long userId);
}
