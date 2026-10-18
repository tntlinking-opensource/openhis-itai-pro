/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.catalog.assembler;

import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;
import com.zxplat.plugin.chs.gateway.scheme.catalog.model.Cat3301Input;

import com.zxplat.plugin.chs.domain.catalog.command.UploadCatalog3301RowCommand;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.zxplat.plugin.chs.gateway.scheme.catalog.model.Cat3301Input;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import com.zxplat.plugin.chs.gateway.scheme.catalog.model.Cat3301Input;

/** 【3301】目录对照上传入参组装 */
@Component
@ConditionalOnProperty(name = "zxplat.modules.chs.enabled", havingValue = "true", matchIfMissing = true)
public class Catalog3301InputAssembler {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    public Cat3301Input assemble(List<UploadCatalog3301RowCommand> rows) {
        List<Cat3301Input.Cat3301Row> data = new ArrayList<>(rows.size());
        for (UploadCatalog3301RowCommand row : rows) {
            Cat3301Input.Cat3301Row item = new Cat3301Input.Cat3301Row()
                .setFixmedinsHilistId(row.getFixmedinsHilistId())
                .setFixmedinsHilistName(row.getFixmedinsHilistName())
                .setListType(row.getListType())
                .setMedListCodg(row.getMedListCodg())
                .setBegndate(resolveBegndate(row.getBegndate()))
                .setEnddate(resolveEnddate(row.getEnddate()));
            if (isNotEmpty(row.getAprvno())) {
                item.setAprvno(row.getAprvno().trim());
            }
            if (isNotEmpty(row.getSpec())) {
                item.setSpec(row.getSpec().trim());
            }
            if (isNotEmpty(row.getDosform())) {
                item.setDosform(row.getDosform().trim());
            }
            data.add(item);
        }
        Cat3301Input input = new Cat3301Input();
        input.setData(data);
        return input;
    }

    private static String resolveBegndate(String value) {
        if (isNotEmpty(value)) {
            return value.trim();
        }
        return LocalDate.now().format(DATE_FMT);
    }

    private static String resolveEnddate(String value) {
        if (isNotEmpty(value)) {
            return value.trim();
        }
        return "2099-12-31";
    }
}
