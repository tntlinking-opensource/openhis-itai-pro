/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.application.dto;

import lombok.Data;
import java.util.List;

/**
 * 分配用户到机构请求
 */
@Data
public class AssignUsersRequest {

    /**
     * 组织单元ID
     */
    private Long organizationUnitId;

    /**
     * 用户ID列表
     */
    private List<Long> userIds;
}
