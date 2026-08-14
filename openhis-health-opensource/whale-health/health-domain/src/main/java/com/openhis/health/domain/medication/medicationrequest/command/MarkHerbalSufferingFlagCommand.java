/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationrequest.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 补充代煎费前：将方内饮片行代煎标志置为是。
 */
@Data
@Accessors(chain = true)
public class MarkHerbalSufferingFlagCommand {

    /** 方内饮片申请主键列表。 */
    private List<Long> medicationRequestIds = new ArrayList<>();

    /** 操作人。 */
    private Long operatorId;
}
