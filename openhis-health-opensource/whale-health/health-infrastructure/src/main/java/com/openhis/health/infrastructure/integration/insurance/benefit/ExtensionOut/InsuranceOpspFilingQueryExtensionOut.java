/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 慢特病备案查询结果。
 *
 * <p>职责：封装平台返回的慢特病备案有效行列表。</p>
 */
@Data
@Accessors(chain = true)
public class InsuranceOpspFilingQueryExtensionOut {

    /** 慢特病备案明细行。 */
    private List<InsuranceOpspFilingLineSnapshot> filingLines = new ArrayList<>();
}
