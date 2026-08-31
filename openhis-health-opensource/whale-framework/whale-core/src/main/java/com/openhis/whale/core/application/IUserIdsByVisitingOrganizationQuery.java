/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.application;

import java.util.List;

/**
 * 按 HIS 出诊科室解析平台用户主键（health 模块实现）。
 *
 * <p>身份域 {@code UserAppService#getList} 不直接读 {@code adm_pr_assign}：由本 Port
 * 把「科室下挂了激活医师出诊岗」的医务人员映射为 {@code whale_users.id}。</p>
 */
public interface IUserIdsByVisitingOrganizationQuery {

    /**
     * 在指定科室挂了激活医师出诊岗（{@code ROLE_DOCTOR} + {@code adm_pr_assign.org_id}）的平台用户主键。
     *
     * <p>无命中或无法解析医师角色时返回空列表；调用方须据此得到空分页，不得忽略筛选条件。</p>
     *
     * @param organizationId HIS 科室主键（{@code adm_organization.id}）；空时返回空列表
     * @return 去重后的用户主键；永不 {@code null}
     */
    List<Long> findUserIdsByVisitingOrganizationId(Long organizationId);
}
