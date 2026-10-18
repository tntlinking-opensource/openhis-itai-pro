/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 变更名册所属体检分组请求。
 */
@Getter
@Setter
public class UpdateRosterBatchRequest {

    /** 名册主键。 */
    private Long rosterId;

    /** 目标体检分组主键。 */
    private Long targetBatchId;
}
