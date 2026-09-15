/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.chargeitem.query;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 重点药品统计轻量行（AS-RPT-011 性能路径）：仅承载就诊、药典主键与带符号售价。
 *
 * <p>由 {@link com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService
 * #listKeyMedicationUsageLines} 产出，禁止物化 {@link OutpatientFeeDetailChargeRowQueryModel}。</p>
 */
@Getter
@Setter
public class KeyMedicationUsageLineQueryModel {

    /** 就诊主键 {@code encounter_id}。 */
    private Long encounterId;

    /** 药典产品主键 {@code product_id}（{@code product_table = med_medication_definition}）。 */
    private Long productId;

    /** 带收退符号的行售价 {@code total_price}。 */
    private BigDecimal signedSale;
}
