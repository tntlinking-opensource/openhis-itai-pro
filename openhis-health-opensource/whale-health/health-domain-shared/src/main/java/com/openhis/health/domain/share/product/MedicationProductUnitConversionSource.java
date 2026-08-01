/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.product;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 药品五类单位与相对拆零倍数（发药/库存断言用），与 {@code ProductDefinitionReadModel} 解耦。
 */
@Getter
@Setter
public class MedicationProductUnitConversionSource {

    /** split单位编码（MedicationProductUnitConversionSource）。 */
    private String splitUnitCode;
    /** dose单位编码（MedicationProductUnitConversionSource）。 */
    private String doseUnitCode;
    /** dose单位Ratio（MedicationProductUnitConversionSource）。 */
    private BigDecimal doseUnitRatio;
    /** purchase单位编码（MedicationProductUnitConversionSource）。 */
    private String purchaseUnitCode;
    /** purchase单位Ratio（MedicationProductUnitConversionSource）。 */
    private BigDecimal purchaseUnitRatio;
    /** stock单位编码（MedicationProductUnitConversionSource）。 */
    private String stockUnitCode;
    /** stock单位Ratio（MedicationProductUnitConversionSource）。 */
    private BigDecimal stockUnitRatio;
    /** package单位编码（MedicationProductUnitConversionSource）。 */
    private String packageUnitCode;
    /** package单位Ratio（MedicationProductUnitConversionSource）。 */
    private BigDecimal packageUnitRatio;
    /**
     * 追溯码贴码单位（可游离于五类业务单位之外；与 {@link #traceUnitRatio} 成对）。
     */
    private String traceUnitCode;
    /**
     * 1 个追溯码单位对应的拆零数量；须 &gt; 0 方可参与换算。
     */
    private BigDecimal traceUnitRatio;
}
