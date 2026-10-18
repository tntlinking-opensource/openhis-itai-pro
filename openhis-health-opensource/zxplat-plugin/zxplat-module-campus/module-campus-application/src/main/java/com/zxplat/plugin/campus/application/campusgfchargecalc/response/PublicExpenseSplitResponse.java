/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.campusgfchargecalc.response;

import com.zxplat.plugin.campus.application.campusarchive.response.EligibilityCheckResponse;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class PublicExpenseSplitResponse {

    private EligibilityCheckResponse eligibility;
    private List<Line> lines = new ArrayList<>();
    private BigDecimal gfSum;
    private BigDecimal selfSum;

    @Getter
    @Setter
    public static class Line {
        private String itemCode;
        private String chargeModeEnum;
        private BigDecimal ratio;
        private BigDecimal gfAmount;
        private BigDecimal selfAmount;
    }
}
