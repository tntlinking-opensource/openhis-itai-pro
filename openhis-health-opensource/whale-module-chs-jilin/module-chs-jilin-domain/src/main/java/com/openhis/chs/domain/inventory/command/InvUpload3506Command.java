/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 【3506】商品销售退货上传 */
@Data
@Accessors(chain = true)
public class InvUpload3506Command {

    private String contractBusNo;
    private Long contractId;
    private Long encounterId;
    private Long sourceId;
    private List<InvUploadLineCommand> lines = new ArrayList<>();
}
