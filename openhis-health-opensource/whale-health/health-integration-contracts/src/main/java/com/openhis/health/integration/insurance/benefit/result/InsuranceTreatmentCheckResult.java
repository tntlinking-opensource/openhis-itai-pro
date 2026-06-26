/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.benefit.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 待遇享受检查结果。 */
@Data
@Accessors(chain = true)
public class InsuranceTreatmentCheckResult {

    private List<InsuranceTreatmentLineSnapshot> treatmentLines = new ArrayList<>();
}
