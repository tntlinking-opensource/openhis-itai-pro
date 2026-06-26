/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.benefit.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 人员累计信息查询结果。 */
@Data
@Accessors(chain = true)
public class InsuranceCumulativeQueryResult {

    private List<InsuranceCumulativeLineSnapshot> cumulativeLines = new ArrayList<>();
}
