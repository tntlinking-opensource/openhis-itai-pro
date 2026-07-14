/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.reconciliation.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 【3209A】跨省三方对账失败行 */
@Data
@Accessors(chain = true)
public class CrossProvinceReconRowResult {

    private String setlId;
    private String insuAdmdvs;
}
