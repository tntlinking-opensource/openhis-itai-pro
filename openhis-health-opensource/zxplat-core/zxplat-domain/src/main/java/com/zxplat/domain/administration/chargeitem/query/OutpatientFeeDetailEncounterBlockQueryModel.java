/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.chargeitem.query;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 门诊费用明细：单就诊块读模型（DM-ADM-011 §6.2.25；块内行按 payment {@code bill_date} 倒序）。
 */
@Getter
@Setter
public class OutpatientFeeDetailEncounterBlockQueryModel {

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 块内明细行（按 billDate 倒序）。 */
    private List<OutpatientFeeDetailChargeRowQueryModel> lines = new ArrayList<>();
}
