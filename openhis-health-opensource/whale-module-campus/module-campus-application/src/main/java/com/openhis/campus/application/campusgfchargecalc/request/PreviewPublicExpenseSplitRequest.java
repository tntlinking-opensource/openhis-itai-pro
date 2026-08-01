/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.campusgfchargecalc.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class PreviewPublicExpenseSplitRequest {

    private Long tenantId;

    @NotNull
    private Long patientId;

    @NotBlank
    private String mode;

    @NotEmpty
    @Valid
    private List<Line> lines = new ArrayList<>();

    @Getter
    @Setter
    public static class Line {

        private String itemCode;
        private String catalogCode;

        @NotNull
        private BigDecimal amount;

        private String classEnum;
    }
}
