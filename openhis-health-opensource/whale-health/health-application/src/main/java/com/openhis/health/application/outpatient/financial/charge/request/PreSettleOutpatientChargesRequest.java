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

package com.openhis.health.application.outpatient.financial.charge.request;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 门诊预结算请求。
 */
@Data
@Accessors(chain = true)
public class PreSettleOutpatientChargesRequest {

    /** 就诊主键。 */
    private Long encounterId;

    /** 本次拟预结算的收费明细主键列表。 */
    private List<Long> chargeItemIds;

    /** 费用性质合同主键（可选）。 */
    private Long contractId;

    /** 医保介质类型（读卡渠道口径）。 */
    private String ybMediumType;

    /** 医保读卡原始信息（JSON/密文，由前端传入）。 */
    private String ybCardInfo;

    /** 1101 人员编号（读卡后填入）。 */
    private String psnNo;

    /** 参保地医保区划（1101 返回）。 */
    private String insuplcAdmdvs;

    /** 险种类型（可选，默认取 1101/账户）。 */
    private String insutype;

    /** 医疗类别（可选，默认普通门诊 11）。 */
    private String medType;
}
