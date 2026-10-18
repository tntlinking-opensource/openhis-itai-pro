/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationdispense.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 住院护士站汇总核发后追溯码分配命令（**AS-IP-NUR-015 · IF-IP-TRC-04**）。
 *
 * <p>仅更新 {@code trace_no}，不迁移发放状态、不发布发药/追溯 OUT 事件。</p>
 */
@Getter
@Setter
public class AssignTraceNoToCompletedDispenseCommand extends BatchCommand<AssignTraceNoToCompletedDispenseCommand.Item> {

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 就诊锚点（可选）；非空时须与每条发放行 {@code encounter_id} 一致，防止跨就诊误写。
     */
    private Long encounterId;

    /**
     * 追溯码分配子命令。
     */
    @Getter
    @Setter
    public static class Item {

        /** 药品发放行主键。 */
        private Long id;

        /** 逗号分隔追溯码串；空白表示本行跳过不写。 */
        private String traceNo;
    }
}
