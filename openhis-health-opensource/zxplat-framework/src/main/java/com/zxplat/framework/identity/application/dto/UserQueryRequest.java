/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.application.dto;

import java.util.List;

import com.zxplat.framework.core.application.dto.BaseQueryRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户分页查询请求。
 *
 * <p>{@code organizationId} 为 HIS 科室主键：有值时只返回在该科室挂了激活医师出诊岗的账号。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserQueryRequest extends BaseQueryRequest {

    /** 关键词：匹配用户名、姓名、邮箱、手机号（模糊）。空白表示不按关键词收窄。 */
    private String keyword;

    /** 是否启用；{@code null} 表示不按启用状态过滤。 */
    private Boolean isActive;

    /** 是否锁定；{@code null} 表示不按锁定状态过滤。 */
    private Boolean isLockedOut;

    /**
     * 角色主键多选；非空时筛选拥有其中任一角色的用户（OR）。
     */
    private List<Long> roleIds;

    /**
     * HIS 出诊科室主键（{@code adm_organization.id}）。
     *
     * <p>非空时仅返回在该科室配置了激活医师出诊岗的平台用户；与 {@code roleIds} 同时传入时取交集。
     * 跨科出诊：只要该科室有一条激活医师岗即命中。</p>
     */
    private Long organizationId;
}
