/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 【3501】商品盘存上传 */
@Data
@Accessors(chain = true)
public class InvUpload3501Command {

    private String contractBusNo;
    private Long contractId;
    private String remark;
    private Long sourceId;
    /** 缺省 STOCKTAKE；初始化传 INIT */
    private String sourceType;
    private List<InvUploadLineCommand> lines = new ArrayList<>();
}
