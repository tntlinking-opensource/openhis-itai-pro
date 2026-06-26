/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.platformdict.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/** 最近一次同步日志摘要。 */
@Data
@Accessors(chain = true)
public class PlatformDictSyncLogDto {

    private String dictType;
    private String queryAdmdvs;
    private Integer rowsUpserted;
    private String status;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
