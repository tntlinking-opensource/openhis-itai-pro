/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.domain.prescription.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 取药结果查询 Client 入参。 */
@Data
@Accessors(chain = true)
public class ElepMedResultQuery {

    private Long contractId;
    private String contractBusNo;
    private String hiRxno;
    private java.util.Map<String, Object> platformBody;
}
