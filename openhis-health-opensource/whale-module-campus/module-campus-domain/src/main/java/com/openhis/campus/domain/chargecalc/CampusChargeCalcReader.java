/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.chargecalc;

import com.openhis.campus.domain.chargecalc.view.PublicExpenseSplitQuery;
import com.openhis.campus.domain.chargecalc.view.PublicExpenseSplitView;

/**
 * 公费收费试算 Reader 契约（Batch C 实现）。
 */
public interface CampusChargeCalcReader {

    PublicExpenseSplitView previewSplit(Long tenantId, PublicExpenseSplitQuery query);
}
