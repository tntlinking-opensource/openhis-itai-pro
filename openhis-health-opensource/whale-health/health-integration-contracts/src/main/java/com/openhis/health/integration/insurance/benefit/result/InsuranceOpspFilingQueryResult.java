/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.benefit.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 慢特病备案查询结果。 */
@Data
@Accessors(chain = true)
public class InsuranceOpspFilingQueryResult {

    private List<InsuranceOpspFilingLineSnapshot> filingLines = new ArrayList<>();
}
