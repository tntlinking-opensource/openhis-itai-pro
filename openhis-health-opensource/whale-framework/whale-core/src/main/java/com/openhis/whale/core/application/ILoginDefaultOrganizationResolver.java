/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.application;

import java.util.List;
import java.util.Optional;

/**
 * 登录默认组织解析（health 模块实现）。
 *
 * <p>默认登录科室存 {@code adm_practitioner.org_id}；未维护时回落当前会话医院
 * （{@code adm_organization.id}，{@code type_enum=HOSPITAL}）。</p>
 */
public interface ILoginDefaultOrganizationResolver {

    /**
     * 解析用户默认登录组织。
     *
     * @param userId                         平台用户主键
     * @param sessionHospitalOrganizationId  已选医院（HIS {@code adm_organization.id}）；可为 null
     * @return 默认登录组织主键
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
