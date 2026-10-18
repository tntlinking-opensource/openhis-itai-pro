/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.audit.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/** 3101 fsi_operation_dtos 元素 */
@Data
@Accessors(chain = true)
public class Audit3101OperationRow {

    @JSONField(name = "setl_list_oprn_id")
    private String setlListOprnId;

    @JSONField(name = "oprn_code")
    private String oprnCode;

    @JSONField(name = "oprn_name")
    private String oprnName;

    @JSONField(name = "main_oprn_flag")
    private String mainOprnFlag;

    @JSONField(name = "oprn_date")
    private String oprnDate;
}
