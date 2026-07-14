/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.security;

import java.util.List;

/**
 * 当前用户接口
 * 包含身份信息及会话状态（如当前选择的科室）
 */
public interface ICurrentUser {

    /**
     * 获取当前用户ID
     * @return 用户ID，未登录为null
     */
    Long getCurrentUserId();

    /**
     * 获取当前用户名
     * @return 用户名，未登录为null
     */
    String getCurrentUserName();

    /**
     * 判断是否已登录
     * @return true表示已登录
     */
    boolean isAuthenticated();

    /**
     * 获取当前选择的科室/组织单元ID
     * @return 科室ID，未选择为null
     */
    Long getCurrentOrganizationUnitId();

    /**
     * 设置当前选择的科室/组织单元ID
     * @param organizationUnitId 科室ID
     */
    void setCurrentOrganizationUnitId(Long organizationUnitId);

    /**
     * 清除当前用户已选科室（会话 Store + 当前请求上下文）。
     *
     * <p>HTTP 请求结束时的请求级清理由 {@link OrganizationUnitContextInterceptor} 负责，
     * 不会删除 SessionStore 中的用户选择。</p>
     */
    void clearOrganizationUnit();

    /**
     * 获取服务端会话中已选租户（权威来源，非请求头）。
     */
    Long getSessionTenantId();

    /**
     * 保存用户选择的租户到服务端会话。
     */
    void setSessionTenantId(Long tenantId);

    /**
     * 清除服务端已选租户（登出时调用）。
     */
    void clearSessionTenant();

    /**
     * 获取服务端会话中已选医院（HIS {@code adm_organization.id}）。
     */
    Long getSessionHospitalId();

    /**
     * 保存用户选择的医院到服务端会话。
     */
    void setSessionHospitalId(Long hospitalOrganizationId);

    /**
     * 清除当前租户下已选医院。
     */
    void clearSessionHospital();

    /**
     * 获取当前医务人员的ID
     * @return 医务人员ID，未关联为null
     */
    Long getCurrentPractitionerId();

    /**
     * 当前登录用户可操作的药库 + 药房主键集合（均为 {@code adm_location.id}，来自 {@code adm_pr_assign}）。
     *
     * <p>药库岗位 {@code WAREHOUSE_ADMIN}、药房岗位 {@code PHARMACIST} 在人员维护中绑定 {@code location_id}，
     * 与科室（{@code adm_organization.id}）、病区（{@code adm_location} 之 WARD）主键空间不同。</p>
     *
     * @return 去重后的 location id 列表，永不为 {@code null}；绑定医务人员且 strict 模式下 empty 表示无可操作库房
     */
    List<Long> getManagedLocationIds();

    /**
     * 当前登录用户管辖的病区位置主键集合（护士岗位绑定 location）。
     *
     * @return 去重后的病区 location id 列表，永不为 {@code null}；empty 表示过渡态不收窄
     */
    List<Long> getManagedWardLocationIds();

    /**
     * 当前登录用户管辖的科室/组织单元主键集合（出诊/责任/护士科室等岗位 org）。
     *
     * @return 去重后的 org id 列表，永不为 {@code null}；empty 表示过渡态不收窄
     */
    List<Long> getManagedOrganizationUnitIds();

    /**
     * 当前用户已绑定医务人员且 health 模块已注入管辖解析器时为 {@code true}。
     *
     * <p>为 {@code true} 时，各维度管辖集合 empty 表示<strong>无权限</strong>（须收窄/拒绝），
     * 而非「不限」。</p>
     *
     * @return 是否启用岗位派生数据管辖严格模式
     */
    boolean isPractitionerDataScopeActive();

    /**
     * 清理本请求内缓存（岗位管辖解析等）。必须在每个 HTTP 请求开始时调用，
     * 避免线程池复用导致切换用户后仍读到上一用户的管辖范围。
     */
    void clearRequestCache();

    /**
     * 代理者用户 ID；非代理登录时为 null（对齐 ABP {@code FindImpersonatorUserId()}）。
     */
    Long getImpersonatorUserId();

    /**
     * 代理者用户名；非代理登录时为 null。
     */
    String getImpersonatorUserName();

    /**
     * 代理者租户 ID；非代理登录或未记录时为 null。
     */
    Long getImpersonatorTenantId();

    /**
     * 当前是否处于代理登录会话。
     */
    default boolean isImpersonating() {
        return getImpersonatorUserId() != null;
    }
}
