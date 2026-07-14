/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.outpatient.model;

import com.openhis.chs.gateway.scheme.common.model.ChsDiseInfoRow;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** 【2203】就诊信息上传 input */
@Data
@Accessors(chain = true)
public class Op2203Input {

    private Op2203MdtrtInfo mdtrtinfo;

    private List<ChsDiseInfoRow> diseinfo;
}
