/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.medicalinfo.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 5201–5301 查询命令公共字段（契约 + 人员编号 + 参保地） */
@Data
@Accessors(chain = true)
public class MedicalInfoQueryBaseCommand {

    private String contractBusNo;
    private Long contractId;
    private String psnNo;
    /**
     * 参保地医保区划（可选）。
     *
     * <p>跨省人员查询时须传 1101 返回的参保地；缺省时网关回落定点就医地，可能导致备案/累计查空。</p>
     */
    private String insuplcAdmdvs;
}
