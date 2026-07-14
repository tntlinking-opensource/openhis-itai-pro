/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.inpatient.model;

import com.openhis.chs.gateway.scheme.common.model.wrapper.ChsDataListInput;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/** 【2302】住院费用明细撤销 input（data[]） */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class Ip2302Input extends ChsDataListInput<Ip2302FeeRevokeRow> {
}
