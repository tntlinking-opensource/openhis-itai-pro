/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.reconciliation.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 本地日对账流水列表（出参）。 */
@Data
@Accessors(chain = true)
public class ListLocalReconDailyResponse {

    /** 日对账流水行 */
    private List<LocalReconDailyResponse> rows = new ArrayList<>();
}
