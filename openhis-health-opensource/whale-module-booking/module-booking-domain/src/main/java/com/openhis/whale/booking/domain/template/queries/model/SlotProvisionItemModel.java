/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.template.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * T1 时段项（领域读模型）。
 */
@Getter
@Setter
public class SlotProvisionItemModel {

    private String startTime;

    private String endTime;

    private Integer capacity;
}
