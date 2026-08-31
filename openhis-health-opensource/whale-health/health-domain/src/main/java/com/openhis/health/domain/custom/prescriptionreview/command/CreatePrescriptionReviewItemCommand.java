/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.prescriptionreview.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 批量点评子项：单个处方锚点。
 */
@Getter
@Setter
public class CreatePrescriptionReviewItemCommand {

    /** 处方号（业务号由领域 PRV 日序自生成，子项不传）。 */
    private String prescriptionNo;
}
