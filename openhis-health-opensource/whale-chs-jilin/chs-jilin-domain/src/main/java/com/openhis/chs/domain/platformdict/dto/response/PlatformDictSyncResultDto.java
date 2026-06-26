/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.platformdict.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.Map;

/** 1901 同步结果。 */
@Data
@Accessors(chain = true)
public class PlatformDictSyncResultDto {

    private String dictType;
    private String queryAdmdvs;
    private int rowsUpserted;
    private String status;
    private LocalDateTime finishedAt;
}
