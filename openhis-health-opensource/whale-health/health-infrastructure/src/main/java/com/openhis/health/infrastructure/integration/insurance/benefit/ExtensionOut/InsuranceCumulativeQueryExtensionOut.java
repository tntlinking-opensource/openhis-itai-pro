/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 人员累计信息查询结果。
 *
 * <p>职责：封装 5206 等累计查询返回的险种累计明细行列表。</p>
 */
@Data
@Accessors(chain = true)
public class InsuranceCumulativeQueryExtensionOut {

    /** 累计信息明细行。 */
    private List<InsuranceCumulativeLineSnapshot> cumulativeLines = new ArrayList<>();
}
