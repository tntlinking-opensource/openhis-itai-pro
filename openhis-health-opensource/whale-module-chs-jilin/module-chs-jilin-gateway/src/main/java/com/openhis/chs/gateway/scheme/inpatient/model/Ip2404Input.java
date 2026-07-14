/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.inpatient.model;

import com.openhis.chs.gateway.scheme.common.model.ChsRevokeData;
import lombok.Data;
import lombok.experimental.Accessors;

/** 【2404/2405】入出院撤销 input（data：mdtrt_id + psn_no） */
@Data
@Accessors(chain = true)
public class Ip2404Input {

    private ChsRevokeData data;
}
