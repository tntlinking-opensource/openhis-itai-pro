/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.domain.register.command;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * build2301Payload 命令（2301 审批上传 input 预览，不发 Win）。
 */
@Data
@Accessors(chain = true)
public class Build2301PayloadCommand {

    /** 就诊 id。 */
    private Long encounterId;
    /** 工伤费用性质 contract id。 */
    private Long contractId;
    /** 审批类型 approve_type（默认见 WiPlatformConstants）。 */
    private String approveType;
    /** 审批开始日期 yyyyMMdd。 */
    private String startDate;
    /** 审批结束日期 yyyyMMdd。 */
    private String endDate;
    /** 终止原因 termin_reason。 */
    private String terminReason;
    /** 工伤认定 id injureAffirmID。 */
    private String injureAffirmId;
    /** 认定机构编码 org_code。 */
    private String orgCode;
    /** 认定机构名称 org_name。 */
    private String orgName;
}
