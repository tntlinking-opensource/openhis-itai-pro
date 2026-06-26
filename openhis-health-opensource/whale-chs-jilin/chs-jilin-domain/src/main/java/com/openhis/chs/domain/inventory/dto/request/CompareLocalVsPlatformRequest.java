/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.inventory.dto.request;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/** 本地流水与平台查询（3508–3513）差异比对请求。 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class CompareLocalVsPlatformRequest extends QueryPlatformInvRequest {

    /** 平台查询交易号：3508 | 3509 | 3510 | 3511 | 3512 | 3513。 */
    private String compareQueryInfno;
}
