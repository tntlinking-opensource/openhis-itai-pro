/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inventory.result;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 本地上报流水查询单行（运维台列表/补传）。
 */
@Data
@Accessors(chain = true)
public class LocalInvUploadResult {

    /** 上报流水主键。 */
    private Long id;

    /** 医保契约业务编号。 */
    private String contractBusNo;

    /** 平台交易号（3501–3506）。 */
    private String infno;

    /** 机构批次流水号（业务单据 busNo，幂等键）。 */
    private String fixmedinsBchno;

    /** 医保目录编码。 */
    private String medListCodg;

    /** 来源业务类型（如 DISPENSE、INBOUND）。 */
    private String sourceType;

    /** 来源业务主键（如就诊 id）。 */
    private Long sourceId;

    /** 来源业务行主键（如发放行 id）。 */
    private Long sourceLineId;

    /** 上报状态（PENDING/SUCCESS/FAILED 等）。 */
    private String uploadStatus;

    /** 平台返回结果码。 */
    private String platformRetRslt;

    /** 平台返回或本地校验失败消息。 */
    private String platformMsg;

    /** 出站请求 JSON 快照（补传可编辑后重放）。 */
    private String requestJson;

    /** 平台响应 JSON 快照。 */
    private String responseJson;

    /** 已重试次数。 */
    private Integer retryCount;

    /** 创建时间（字符串展示）。 */
    private String createdAt;
}
