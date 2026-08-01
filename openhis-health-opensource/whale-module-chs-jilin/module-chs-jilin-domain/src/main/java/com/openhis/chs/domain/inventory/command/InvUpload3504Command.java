/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 【3504】商品采购退货上传 */
@Data
@Accessors(chain = true)
public class InvUpload3504Command {

    private String contractBusNo;
    private Long contractId;
    private String inboundBusNo;
    private Long sourceId;
    /**
     * 操作人主键：异步出站由调用线程写入；空则编排器回落登录态。
     */
    private Long operatorId;
    /**
     * 操作人姓名：异步出站由调用线程写入；空则签到解析回落。
     */
    private String operatorName;
    private List<InvUploadLineCommand> lines = new ArrayList<>();
}
