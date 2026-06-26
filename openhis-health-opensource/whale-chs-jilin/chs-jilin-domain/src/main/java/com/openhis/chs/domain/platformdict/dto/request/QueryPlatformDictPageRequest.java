/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.platformdict.dto.request;

import lombok.Data;
import lombok.experimental.Accessors;

/** 本地 1901 字典镜像分页查询。 */
@Data
@Accessors(chain = true)
public class QueryPlatformDictPageRequest {

    private Integer pageNo;
    private Integer pageSize;

    /** 1901 type；默认 med_type（基线规范字典表）。 */
    private String dictType;

    /** 区划码或地名关键字（模糊）。 */
    private String keyword;
}
