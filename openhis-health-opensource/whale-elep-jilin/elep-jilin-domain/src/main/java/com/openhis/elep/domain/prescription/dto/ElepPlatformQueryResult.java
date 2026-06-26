/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.elep.domain.prescription.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** 【A0005】平台查询结果（Bridge）。 */
@Data
@Accessors(chain = true)
public class ElepPlatformQueryResult {

    private boolean success;
    private String message;
    private String rxStasCodg;
    private String rxUsedStasCodg;
    private String platformJson;
}
