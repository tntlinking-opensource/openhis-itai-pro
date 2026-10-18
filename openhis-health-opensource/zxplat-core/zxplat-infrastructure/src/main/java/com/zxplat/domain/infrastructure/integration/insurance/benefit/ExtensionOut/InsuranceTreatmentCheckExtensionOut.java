/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.benefit.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 待遇享受检查结果。
 *
 * <p>职责：封装待遇检查接口返回的基金/待遇明细行列表。</p>
 */
@Data
@Accessors(chain = true)
public class InsuranceTreatmentCheckExtensionOut {

    /** 待遇检查明细行。 */
    private List<InsuranceTreatmentLineSnapshot> treatmentLines = new ArrayList<>();
}
