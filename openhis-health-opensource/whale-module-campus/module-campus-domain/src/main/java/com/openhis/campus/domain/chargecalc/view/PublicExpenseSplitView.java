/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.chargecalc.view;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 公费拆分试算结果。
 */
@Getter
@Setter
public class PublicExpenseSplitView {

    private Eligibility eligibility = new Eligibility();
    private List<Line> lines = new ArrayList<>();
    private BigDecimal gfSum = BigDecimal.ZERO;
    private BigDecimal selfSum = BigDecimal.ZERO;

    @Getter
    @Setter
    public static class Eligibility {
        private Boolean passed;
        private String reasonCode;
        private String reasonMessage;
        private String gfStatusEnum;
        private String cardNo;
        private String medicalCode;
        private String campusCode;
    }

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
