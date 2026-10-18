/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.medicalinfo.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/** 【5301】人员慢特病备案查询 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class QueryOpspFiling5301Command extends MedicalInfoQueryBaseCommand {
}
