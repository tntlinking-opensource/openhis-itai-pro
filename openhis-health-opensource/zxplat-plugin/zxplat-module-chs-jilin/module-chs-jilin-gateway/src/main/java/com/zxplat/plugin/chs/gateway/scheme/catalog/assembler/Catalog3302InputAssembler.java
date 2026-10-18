/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.catalog.assembler;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import com.zxplat.plugin.chs.gateway.scheme.catalog.model.Cat3302Input;

import com.zxplat.plugin.chs.domain.catalog.command.RevokeCatalog3302RowCommand;
import com.zxplat.plugin.chs.domain.share.constants.ChsErrorCodes;
import com.zxplat.framework.core.application.exception.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 【3302】目录对照撤销入参组装 */
@Component
@ConditionalOnProperty(name = "zxplat.modules.chs.enabled", havingValue = "true", matchIfMissing = true)
public class Catalog3302InputAssembler {

    public Cat3302Input assemble(String fixmedinsCode, RevokeCatalog3302RowCommand row) {
        Cat3302Input.Cat3302Data data = new Cat3302Input.Cat3302Data()
            .setFixmedinsCode(fixmedinsCode.trim())
            .setFixmedinsHilistId(row.getFixmedinsHilistId())
            .setListType(row.getListType())
            .setMedListCodg(row.getMedListCodg());
        Cat3302Input input = new Cat3302Input();
        input.setData(data);
        return input;
    }

    public void validateRow(RevokeCatalog3302RowCommand row) {
        if (row == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "3302 撤销行不能为空");
        }
        if (isEmpty(row.getFixmedinsHilistId())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "fixmedins_hilist_id 不能为空");
        }
        if (isEmpty(row.getListType())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "list_type 不能为空");
        }
        if (isEmpty(row.getMedListCodg())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "med_list_codg 不能为空");
        }
    }
}
