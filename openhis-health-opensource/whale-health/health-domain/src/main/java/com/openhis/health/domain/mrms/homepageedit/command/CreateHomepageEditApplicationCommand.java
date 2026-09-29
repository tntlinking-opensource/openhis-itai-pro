/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.homepageedit.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 创建首页编辑申请命令。
 */
@Getter
@Setter
public class CreateHomepageEditApplicationCommand {

    /** 租户。 */
    private Long tenantId;
    /** 病案主档。 */
    private Long caseRecordId;
    /** 就诊。 */
    private Long encounterId;
    /** 申请原因。 */
    private String applyReason;
    /** 创建时首页 version_no。 */
    private Integer fromVersion;
    /** 首页可编辑分区 JSON 快照。 */
    private String contentJson;
    /** 申请人。 */
    private Long applicantId;
    /** 操作人（通常同申请人）。 */
    private Long operatorId;
}
