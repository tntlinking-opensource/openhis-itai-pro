/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.platformdict.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/** 最近一次同步日志摘要 */
@Data
@Accessors(chain = true)
public class PlatformDictSyncLogResult {

    private String dictType;
    private String queryAdmdvs;
    private Integer rowsUpserted;
    private String status;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
