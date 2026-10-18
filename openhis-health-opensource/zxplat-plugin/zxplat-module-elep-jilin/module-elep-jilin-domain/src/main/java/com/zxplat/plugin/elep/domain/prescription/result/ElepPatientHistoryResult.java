/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.domain.prescription.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 患者历史处方分页结果。 */
@Data
@Accessors(chain = true)
public class ElepPatientHistoryResult {

    private List<ElepPatientHistoryItem> items = new ArrayList<>();
    private long total;
    private int page;
    private int pageSize;
}
