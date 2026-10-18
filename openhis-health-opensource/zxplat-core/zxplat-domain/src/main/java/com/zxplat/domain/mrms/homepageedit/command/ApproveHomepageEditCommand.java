/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.mrms.homepageedit.command;

import com.zxplat.domain.mrms.caserecord.command.HomepageSaveCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 同意首页编辑申请命令：含应用层从工作稿 JSON 解析出的首页保存命令。
 *
 * <p>领域校验申请状态与 from_version 后，用 {@link #homepageSaveCommand} 回写首页（跳过费用由 Homepage Domain 保证）。</p>
 */
@Getter
@Setter
public class ApproveHomepageEditCommand {

    /** 编辑申请主键。 */
    private Long applicationId;

    /** 审核人 practitionerId。 */
    private Long reviewerId;

    /**
     * 由工作稿解析的首页保存命令（caseRecordId/operatorId 由领域覆盖为申请上下文）。
     */
    private HomepageSaveCommand homepageSaveCommand;
}
