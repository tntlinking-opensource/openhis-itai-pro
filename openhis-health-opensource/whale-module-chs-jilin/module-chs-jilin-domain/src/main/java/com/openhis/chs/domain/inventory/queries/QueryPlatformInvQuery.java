/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.queries;

import lombok.Data;
import lombok.experimental.Accessors;

/** 【3508–3513】平台进销存查询入参 */
@Data
@Accessors(chain = true)
public class QueryPlatformInvQuery {

    private String contractBusNo;
    private Long contractId;
    private String medinsListCodg;
    private String fixmedinsBchno;
    private String begndate;
    private String enddate;
    private String medListCodg;
    private String medinsProdSelNo;
    private String medinsProdPurcNo;
    private String medinsProdInvNo;
    private String psnNo;
    private String certno;
    private String mdtrtId;
    private String drugTracCodg;
    private String valiFlag;
    /**
     * 异步出站操作人主键（调用线程快照）；空则 Invoker 回落当前登录态。
     * <p>退药 3506 组装期查 3511 发生在 {@code @Async} 线程，无 SecurityContext，必须带入。</p>
     */
    private Long operatorId;
    /** 异步出站操作人姓名；可空，签到侧可回落。 */
    private String operatorName;
}
