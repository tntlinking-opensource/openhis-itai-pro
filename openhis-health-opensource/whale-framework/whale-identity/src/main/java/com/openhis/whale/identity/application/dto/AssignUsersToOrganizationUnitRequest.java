/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.application.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

/**
 * 分配用户到组织单元请求
 */
@Data
public class AssignUsersToOrganizationUnitRequest {

    @NotEmpty(message = "用户ID列表不能为空")
    private List<Long> userIds;
}
