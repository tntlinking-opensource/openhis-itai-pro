/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.supplyrequest.queries;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 按多个业务单号批量查询供应请求（一览、批量门禁等）。
 */
@Getter
@Setter
public class SupplyByBusNoBatchQuery {

    /** 业务单号列表；与仓储 {@code findByBusNoIn} 一致。 */
    private List<String> busNoList;
}
