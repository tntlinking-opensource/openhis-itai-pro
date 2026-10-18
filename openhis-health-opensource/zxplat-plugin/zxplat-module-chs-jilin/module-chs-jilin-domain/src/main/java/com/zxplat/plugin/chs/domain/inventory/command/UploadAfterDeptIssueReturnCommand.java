/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inventory.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 科室退领审核通过后触发 3502 其他入库上报。 */
@Data
@Accessors(chain = true)
public class UploadAfterDeptIssueReturnCommand {

    private String deptIssueReturnBusNo;
    private List<Long> supplyRequestLineIds = new ArrayList<>();
    private String contractBusNo;
    private Long contractId;
}
