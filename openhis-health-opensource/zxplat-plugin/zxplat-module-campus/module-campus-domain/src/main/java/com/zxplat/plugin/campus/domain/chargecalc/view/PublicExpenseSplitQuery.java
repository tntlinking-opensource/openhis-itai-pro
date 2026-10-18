/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.domain.chargecalc.view;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 公费拆分试算查询。
 */
@Getter
@Setter
public class PublicExpenseSplitQuery {

    private Long patientId;
    private String mode;
    private List<Line> lines = new ArrayList<>();

    @Getter
    @Setter
    public static class Line {
        private String itemCode;
        private String catalogCode;
        private BigDecimal amount;
        private String classEnum;
    }
}
