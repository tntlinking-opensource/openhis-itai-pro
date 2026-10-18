/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.medicalinfo.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/** 【5205】人员慢特病用药记录查询 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class QueryOpspDrug5205Command extends MedicalInfoQueryBaseCommand {

    /** yyyy-MM-dd HH:mm:ss */
    private String begntime;
    /** yyyy-MM-dd HH:mm:ss */
    private String endtime;
}
