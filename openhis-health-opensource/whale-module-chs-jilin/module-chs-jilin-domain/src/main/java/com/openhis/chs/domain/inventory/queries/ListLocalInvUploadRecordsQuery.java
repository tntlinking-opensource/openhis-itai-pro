/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.queries;

import lombok.Data;
import lombok.experimental.Accessors;

/** 本地上报流水列表查询 */
@Data
@Accessors(chain = true)
public class ListLocalInvUploadRecordsQuery {

    private String sourceType;
    private Long sourceId;
    private String infno;
}
