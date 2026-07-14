/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.medicalinfo.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 5201–5301 查询命令公共字段（契约 + 人员编号） */
@Data
@Accessors(chain = true)
public class MedicalInfoQueryBaseCommand {

    private String contractBusNo;
    private Long contractId;
    private String psnNo;
}
