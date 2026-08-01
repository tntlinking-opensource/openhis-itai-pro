/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.application.dto;

import java.util.List;

import com.openhis.whale.core.application.dto.BaseQueryRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserQueryRequest extends BaseQueryRequest {

    private String keyword;
    private Boolean isActive;
    private Boolean isLockedOut;
    /**
     * 角色主键多选；非空时筛选拥有其中任一角色的用户（OR）。
     */
    private List<Long> roleIds;
}
