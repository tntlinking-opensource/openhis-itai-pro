/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.share.constants;

import java.math.BigDecimal;

/**
 * 门诊药品处方分方（{@code prescription_no}）规则常量：药品性质码、处方类别码与装箱上限。
 */
public interface PrescriptionSplitConstants {

    /** 系统设置项：门诊处方金额上限（元）；不配时回落 {@link #DEFAULT_OUTPATIENT_AMOUNT_LIMIT_YUAN}。 */
    String SETTING_OUTPATIENT_PRESCRIPTION_AMOUNT_LIMIT_YUAN = "outpatientPrescriptionAmountLimitYuan";

    /** 未配置系统设置时的默认金额上限（元）。 */
    BigDecimal DEFAULT_OUTPATIENT_AMOUNT_LIMIT_YUAN = new BigDecimal("500");

    /** 不按金额分方时可在系统设置中配置极大值（如 99999）。 */
    BigDecimal OUTPATIENT_AMOUNT_LIMIT_DISABLED_SENTINEL_YUAN = new BigDecimal("99999");

    /** 西药/中成药同一处方内允许的最大品种数。 */
    int MAX_WESTERN_PATENT_MEDICATION_KINDS_PER_PRESCRIPTION = 5;

    /** 药品目录：普通药品（{@code part_attribute_enum}）。 */
    String PART_ATTRIBUTE_NORMAL = "1";

    /** 药品目录：麻醉药品。 */
    String PART_ATTRIBUTE_NARCOTIC = "2";

    /** 药品目录：毒性药品。 */
    String PART_ATTRIBUTE_TOXIC = "3";

    /** 药品目录：一类精神药品。 */
    String PART_ATTRIBUTE_PSYCH_ONE = "4";

    /** 药品目录：二类精神药品。 */
    String PART_ATTRIBUTE_PSYCH_TWO = "5";

    /** 处方类别码（355）：门诊西药（含中成药同方）。 */
    String RX_TYPE_OUTPATIENT_WESTERN = "1";

    /** 处方类别码（355）：门诊中药饮片。 */
    String RX_TYPE_OUTPATIENT_HERBAL = "2";

    /** 处方类别码（355）：急诊西药。 */
    String RX_TYPE_EMERGENCY_WESTERN = "3";

    /** 处方类别码（355）：急诊中药饮片。 */
    String RX_TYPE_EMERGENCY_HERBAL = "4";

    /** 处方类别码（355）：儿科西药。 */
    String RX_TYPE_PEDIATRIC_WESTERN = "5";

    /** 处方类别码（355）：儿科中药饮片。 */
    String RX_TYPE_PEDIATRIC_HERBAL = "6";

    /** 处方类别码（355）：麻、精一。 */
    String RX_TYPE_NARCOTIC_PSYCH_ONE = "7";

    /** 分方装箱：西药中成药轨道。 */
    String SPLIT_TRACK_WESTERN_PATENT = "WESTERN_PATENT";

    /** 分方装箱：中药饮片轨道。 */
    String SPLIT_TRACK_HERBAL = "HERBAL";

    /** 分方装箱：毒性药品轨道。 */
    String SPLIT_TRACK_TOXIC = "TOXIC";

    /** 分方装箱：麻/精一药品轨道。 */
    String SPLIT_TRACK_NARCOTIC_PSYCH_ONE = "NARCOTIC_PSYCH_ONE";

    /** 分方分区键分隔符（业务键不含此字符）。 */
    String PARTITION_KEY_SEPARATOR = "\u0001";
}
