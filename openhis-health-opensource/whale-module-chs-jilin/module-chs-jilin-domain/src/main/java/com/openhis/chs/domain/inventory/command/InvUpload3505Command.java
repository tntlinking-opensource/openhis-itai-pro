/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 【3505】商品销售上传 */
@Data
@Accessors(chain = true)
public class InvUpload3505Command {

    private String contractBusNo;
    private Long contractId;
    private Long encounterId;
    private Long sourceId;
    /**
     * 本地来源类型（写入 {@code yb_inv_upload.source_type}）；
     * 空则编排器回落 {@code DISPENSE}。
     */
    private String sourceType;
    /**
     * 操作人主键：异步出站由调用线程写入（对齐发票 {@code BossIssueSnapshot.operatorId}）；
     * 空则编排器回落 {@code requireOperatorId}（运维台同步路径）。
     */
    private Long operatorId;
    /**
     * 操作人姓名：异步出站由调用线程写入，供平台 InvokeContext；空则签到解析回落。
     */
    private String operatorName;
    private List<InvUploadLineCommand> lines = new ArrayList<>();
}
