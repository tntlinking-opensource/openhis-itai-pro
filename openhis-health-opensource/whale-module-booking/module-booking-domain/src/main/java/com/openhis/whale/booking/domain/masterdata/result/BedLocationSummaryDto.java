/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.masterdata.result;

import lombok.Getter;
import lombok.Setter;

/**
 * 病区下床位摘要（读 Location）。
 */
@Getter
@Setter
public class BedLocationSummaryDto {

    /** 床位 location 主键。 */
    private Long bedLocationId;

    /** 床位名称。 */
    private String bedName;

    /** 运营状态 code（如 NOT_IN_USE / RESERVED / IN_USE）。 */
    private String operationalEnum;
}
