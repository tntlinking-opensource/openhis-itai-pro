/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.outpatient.result;

import com.openhis.chs.domain.outpatient.command.YbTreatmentInfoRow;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 2001 待遇检查结果 */
@Data
@Accessors(chain = true)
public class YbTreatmentCheckResult {

    private List<YbTreatmentInfoRow> trtinfoList = new ArrayList<>();
}
