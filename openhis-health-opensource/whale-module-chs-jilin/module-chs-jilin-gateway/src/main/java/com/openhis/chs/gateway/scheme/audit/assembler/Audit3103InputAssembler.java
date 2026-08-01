/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.audit.assembler;

import com.openhis.chs.domain.audit.command.AuditFeedback3103Command;
import com.openhis.chs.domain.audit.command.AuditFeedbackWarnCommand;
import com.openhis.chs.gateway.scheme.audit.model.Audit3103Input;
import com.openhis.chs.gateway.scheme.audit.model.Audit3103WarnRow;

import java.util.ArrayList;
import java.util.List;

/**
 * 【3103】Command → 强类型 Input。
 *
 * <p>{@code warn_rslt_id} 直接取 Command 的 warnRsltId（调用方须填 jrdId）。</p>
 */
public final class Audit3103InputAssembler {

    private Audit3103InputAssembler() {
    }

    /**
     * 组装 3103 平台 input。
     *
     * @param command 反馈命令
     * @return 强类型 Input
     */
    public static Audit3103Input assemble(AuditFeedback3103Command command) {
        Audit3103Input.Audit3103Data data = new Audit3103Input.Audit3103Data();
        data.setWarnType(command.getWarnType());
        data.setWarns(mapWarns(command.getWarns()));
        Audit3103Input input = new Audit3103Input();
        input.setData(data);
        return input;
    }

    /**
     * Command warns → 平台 warns[]（warn_rslt_id 已由调用方填 jrdId）。
     *
     * @param warns 领域反馈行
     * @return 平台反馈行
     */
    private static List<Audit3103WarnRow> mapWarns(List<AuditFeedbackWarnCommand> warns) {
        List<Audit3103WarnRow> rows = new ArrayList<>();
        if (warns == null) {
            return rows;
        }
        for (AuditFeedbackWarnCommand warn : warns) {
            if (warn == null) {
                continue;
            }
            Audit3103WarnRow row = new Audit3103WarnRow();
            row.setWarnRsltId(warn.getWarnRsltId());
            row.setDspoWay(warn.getDspoWay());
            row.setDspoWayRea(warn.getDspoWayRea());
            rows.add(row);
        }
        return rows;
    }
}
