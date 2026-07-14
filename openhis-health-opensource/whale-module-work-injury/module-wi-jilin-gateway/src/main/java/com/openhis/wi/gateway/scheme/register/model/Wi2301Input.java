/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.gateway.scheme.register.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

/**
 * 【2301】审批信息上传 input（10-P0 §5.1）。
 */
@Data
@Accessors(chain = true)
public class Wi2301Input {

    /** 人员编号；1101 / anchor。 */
    @JSONField(name = "psn_no")
    private String psnNo;

    /** 审批类型；院点多为 12。 */
    @JSONField(name = "approveType")
    private String approveType;

    /** 转诊机构编码；转诊类必填。 */
    @JSONField(name = "orgCode")
    private String orgCode;

    /** 转诊机构名称。 */
    @JSONField(name = "orgName")
    private String orgName;

    /** 审批开始 yyyyMMdd。 */
    @JSONField(name = "startDate")
    private String startDate;

    /** 审批结束 yyyyMMdd；空则 startDate +30 天。 */
    @JSONField(name = "endDate")
    private String endDate;

    /** 终止原因 terminReason。 */
    @JSONField(name = "terminReason")
    private String terminReason;

    /** 工伤认定 ID；1101 diagnosis.injureAffirmID。 */
    @JSONField(name = "injureAffirmID")
    private String injureAffirmId;

    /** 辅助器具申请（可选扩展）。 */
    @JSONField(name = "auxiliaryApplianceApply")
    private List<Map<String, Object>> auxiliaryApplianceApply;
}
