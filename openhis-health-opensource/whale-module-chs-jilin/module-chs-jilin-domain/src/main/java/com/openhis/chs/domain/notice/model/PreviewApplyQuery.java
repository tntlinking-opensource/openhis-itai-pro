/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.notice.model;

import lombok.Data;

/**
 * 应用差异预览查询（R-06）。
 */
@Data
public class PreviewApplyQuery {

    /** 租户。 */
    private Long tenantId;

    /** 文件头主键。 */
    private Long docId;
}
