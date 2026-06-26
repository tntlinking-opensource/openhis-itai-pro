/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.platformdict.dto.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/** 本地 1901 字典镜像行 DTO。 */
@Data
@Accessors(chain = true)
public class PlatformDictItemDto {

    private Long id;
    private String dictType;
    private String dictValue;
    private String dictLabel;
    private String parentValue;
    private Integer sortOrder;
    private String valiFlag;
    private String queryAdmdvs;
    private LocalDateTime syncedAt;
}
