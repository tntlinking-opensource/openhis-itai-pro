/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.extension.peis;

import lombok.Getter;
import lombok.Setter;

/**
 * 团检选定 / 换套餐结果。
 */
@Getter
@Setter
public class PeisGroupRosterSelectResult {

    private Long rosterId;
    private Long encounterId;
    private String busNo;
    private Long selectedPackageActivityId;
}
