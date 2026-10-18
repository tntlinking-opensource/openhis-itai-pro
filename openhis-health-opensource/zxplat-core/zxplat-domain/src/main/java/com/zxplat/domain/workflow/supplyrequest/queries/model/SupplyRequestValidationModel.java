/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.supplyrequest.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 供应请求简单校验读模型：某业务单号下是否已存在「已同意」行（用于重复同意等门控）。
 *
 * <p>{@code hasAgreed} 由仓储 {@code existsByBusNoAndStatusEnum(busNo, SupplyStatus.AGREE.getCode())} 推导，与库表 {@code status_enum} 持久化 code 一致（B.10.8.1）。</p>
 */
@Getter
@Setter
public class SupplyRequestValidationModel {

    /** 业务单号。 */
    private String busNo;
    /** 是否已存在至少一行 {@code status_enum} 为已同意（{@link com.zxplat.domain.share.enums.SupplyStatus#AGREE} 的 {@code getCode()}）。 */
    private Boolean hasAgreed;
}
