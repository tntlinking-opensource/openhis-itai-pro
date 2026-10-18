/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.diagnosticreport.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 保存总检综述草稿命令（DM-CLN-008 §5.1.2）。
 */
@Getter
@Setter
public class SaveMasterDraftCommand {

    /** 就诊主键。 */
    private Long encounterId;

    /** 综述文本。 */
    private String summaryText;

    /** 结论文本。 */
    private String conclusionText;

    /** 结果引用 JSON。 */
    private String resultRefsJson;

    /** 操作人。 */
    private Long operatorId;
}
