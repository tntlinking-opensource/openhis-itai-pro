/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.domain.register.command;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * bindQualification 命令（9102 查询结果用户所选审批资格行）。
 */
@Data
@Accessors(chain = true)
public class BindQualificationCommand {

    /** 就诊 id。 */
    private Long encounterId;
    /** 工伤费用性质 contract id。 */
    private Long contractId;
    /** 9102 返回的 qualification_id。 */
    private String qualificationId;
}
