/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.application.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.List;

/**
 * 当前用户DTO
 */
@Data
public class CurrentUserDto {

    /**
     * 是否已认证
     */
    private Boolean isAuthenticated;

    /**
     * 用户ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 租户ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    /**
     * 用户名
     */
    private String userName;

    /**
     * 姓名
     */
    private String name;

    /**
     * 姓氏
     */
    private String surName;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 邮箱是否已验证
     */
    private Boolean emailVerified;

    /**
     * 手机号
     */
    private String phoneNumber;

    /**
     * 手机号是否已验证
     */
    private Boolean phoneNumberVerified;

    /**
     * 角色列表
     */
    private List<String> roles;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 代理用户ID（如果当前是代理登录）
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long impersonatorUserId;

    /**
     * 代理租户ID（如果当前是代理登录）
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long impersonatorTenantId;

    /**
     * 代理用户名（如果当前是代理登录）
     */
    private String impersonatorUserName;

    /**
     * 代理租户名（如果当前是代理登录）
     */
    private String impersonatorTenantName;

    /**
     * 医务人员主键（{@code adm_practitioner.id}）
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long practitionerId;

    /**
     * 行政机构主键（{@code adm_organization.id}，来自医务人员主档 {@code org_id}）
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long organizationId;

    /**
     * 是否具备财务审批登录角色（{@code whale_user_roles} 绑定 {@code ROLE_FINANCE_APPROVER}；动账类供应单与调价 approve/reject）。
     */
    private Boolean canFinanceApprove;
}
