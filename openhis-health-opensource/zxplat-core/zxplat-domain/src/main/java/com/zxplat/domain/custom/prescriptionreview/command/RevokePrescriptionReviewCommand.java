/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.prescriptionreview.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 撤销处方点评命令（DM-CUS-015 §5.1.3）。
 *
 * <p>{@code id} 与 {@code prescriptionNo} 二选一，优先 {@code id}。</p>
 */
@Getter
@Setter
public class RevokePrescriptionReviewCommand {

    /** 点评主键。 */
    private Long id;
    /** 处方号锚点。 */
    private String prescriptionNo;
    /** 操作人。 */
    private Long operatorId;
}
