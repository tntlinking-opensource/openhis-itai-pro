/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.catalog.mapper;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;

import com.openhis.chs.bridge.catalog.support.ChsDateTimeParseUtil;
import com.openhis.chs.domain.catalog.command.UpsertInstitutionCatalogMatchCommand;
import com.openhis.chs.domain.share.enums.ListType;
import com.openhis.chs.gateway.scheme.catalog.model.Cat1317RowOutput;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 1317 平台行 → 领域 upsert 命令映射（无业务流程）。
 */
@Component
public class Catalog1317PlatformMapper {

    /**
     * 将平台返回行批量转换为 upsert 命令。
     *
     * @param rows 平台输出；null/空返回空列表
     * @return upsert 命令列表
     */
    public List<UpsertInstitutionCatalogMatchCommand> toUpsertCommands(List<Cat1317RowOutput> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<UpsertInstitutionCatalogMatchCommand> commands = new ArrayList<>(rows.size());
        for (Cat1317RowOutput row : rows) {
            if (row == null || isEmpty(row.getMedinsListCodg()) || isEmpty(row.getMedListCodg())) {
                continue;
            }
            commands.add(new UpsertInstitutionCatalogMatchCommand()
                .setFixmedinsCode(row.getFixmedinsCode())
                .setMedinsListCodg(row.getMedinsListCodg())
                .setMedinsListName(row.getMedinsListName())
                .setInsuAdmdvs(row.getInsuAdmdvs())
                .setListType(StringUtils.defaultIfBlank(row.getListType(), ListType.WESTERN_DRUG.getCode()))
                .setMedListCodg(row.getMedListCodg())
                .setBegndate(parseDate(row.getBegndate()))
                .setEnddate(parseDate(row.getEnddate()))
                .setAprvno(row.getAprvno())
                .setDosform(row.getDosform())
                .setExctCont(row.getExctCont())
                .setItemCont(row.getItemCont())
                .setPrcunt(row.getPrcunt())
                .setSpec(row.getSpec())
                .setPacspec(row.getPacspec())
                .setMemo(row.getMemo())
                .setValiFlag(row.getValiFlag())
                .setRid(row.getRid())
                .setUpdtTime(parseDateTime(row.getUpdtTime()))
                .setPoolareaNo(row.getPoolareaNo()));
        }
        return commands;
    }

    /**
     * 解析平台日期字段（yyyy-MM-dd 或更长字符串取前 10 位）。
     *
     * @param value 平台日期
     * @return LocalDate 或 null
     */
    private static LocalDate parseDate(String value) {
        if (isEmpty(value)) {
            return null;
        }
        String text = value;
        if (value.length() > 10) {
            text = value.substring(0, 10);
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    /**
     * 解析平台日期时间字段。
     *
     * @param value 平台时间
     * @return LocalDateTime 或 null
     */
    private static LocalDateTime parseDateTime(String value) {
        return ChsDateTimeParseUtil.parseLocalDateTime(value);
    }
}
