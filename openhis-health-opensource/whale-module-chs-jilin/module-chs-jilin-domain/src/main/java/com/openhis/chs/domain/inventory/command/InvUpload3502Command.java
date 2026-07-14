/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 【3502】商品库存变更上传 */
@Data
@Accessors(chain = true)
public class InvUpload3502Command {

    private String contractBusNo;
    private Long contractId;
    private String remark;
    private Long sourceId;
    /** {@link com.openhis.chs.domain.share.constants.ChsInventoryConstants} SOURCE_TYPE_* */
    private String sourceType;
    private List<InvUploadLineCommand> lines = new ArrayList<>();
}
