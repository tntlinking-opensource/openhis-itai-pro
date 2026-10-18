/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.supplyrequest.queries;

import lombok.Getter;
import lombok.Setter;

/**
 * 判断指定业务单号下、某物品行是否仍允许编辑（与 {@link com.zxplat.domain.workflow.supplyrequest.service.SupplyRequestQueryService#verifyAbleEdit} 配套）。
 *
 * <p>实现侧按 {@code bus_no} 加载全部行后，定位 {@code item_id} 匹配行，并检查其 {@code status_enum} 是否为审核中/已同意等阻塞态（持久化 code，B.10.8.1）。</p>
 */
@Getter
@Setter
public class VerifyAbleEditQuery {

    /** 业务单号。 */
    private String busNo;
    /** 物品定义主键（与行上 {@code item_id} 对齐）。 */
    private Long itemId;
}
