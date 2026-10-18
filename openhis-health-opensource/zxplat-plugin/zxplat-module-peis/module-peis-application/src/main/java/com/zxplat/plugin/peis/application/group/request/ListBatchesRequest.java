/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 按单位列出团检批次请求。
 */
@Getter
@Setter
public class ListBatchesRequest {

    /** 单位主键。 */
    private Long companyId;
}
