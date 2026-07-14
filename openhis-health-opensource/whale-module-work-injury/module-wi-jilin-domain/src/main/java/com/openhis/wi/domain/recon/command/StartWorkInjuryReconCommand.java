/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.domain.recon.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

/**
 * 工伤对账 invoke 入口命令（1320→1321→1322）。
 */
@Data
@Accessors(chain = true)
public class StartWorkInjuryReconCommand {

    /** 工伤费用性质 fin_contract.id。 */
    private Long contractId;

    /** 操作员 id。 */
    private Long operatorId;

    /** 1320/1321/1322 表单上下文（stmt_begindate / month 等）。 */
    private Map<String, Object> formContext;
}
