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
 * 取消代煎费后回写方内饮片行代煎标志为否。
 */
@Data
@Accessors(chain = true)
public class ClearHerbalSufferingFlagCommand {

    /** 方内饮片申请主键列表。 */
    private List<Long> medicationRequestIds = new ArrayList<>();

    /** 操作人。 */
    private Long operatorId;
}
