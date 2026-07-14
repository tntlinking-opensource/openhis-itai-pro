/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.catalog.mapper;

import com.openhis.chs.domain.share.enums.ListType;
import com.openhis.whale.core.application.util.StringUtils;
import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import com.alibaba.fastjson2.JSON;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.catalog.model.Cat1312RowOutput;
import com.openhis.chs.domain.catalog.result.CatalogDownloadCursorResult;
import com.openhis.chs.domain.catalog.result.NationalCatalogItemResult;
import com.openhis.chs.domain.catalog.command.UpsertNationalCatalogRowCommand;
import com.openhis.chs.domain.catalog.entity.YbCatalogDownloadCursor;
import com.openhis.chs.domain.catalog.entity.YbNationalCatalogItem;
import com.openhis.chs.domain.share.constants.ChsCatalogConstants;
import com.openhis.chs.bridge.catalog.support.ChsDateTimeParseUtil;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 国家目录 DTO / Command 映射实现（纯字段搬运与平台行解析，不承载业务流程判断） */
@Component
public class CatalogPlatformMapperImpl implements CatalogPlatformMapper {

    /**
     * 将国家目录镜像实体映射为列表示 DTO     *
     * @param entity 目录镜像行；{@code null} 时返回 {@code null}
     * @return 列表示 DTO {@code null} */
    @Override
    public NationalCatalogItemResult toDto(YbNationalCatalogItem entity) {
        if (entity == null) {
            return null;
        }
        NationalCatalogItemResult dto = new NationalCatalogItemResult();
        dto.setId(entity.getId());
        dto.setContractId(entity.getContractId());
        dto.setListType(entity.getListType());
        dto.setHilistCode(entity.getHilistCode());
        dto.setHilistName(entity.getHilistName());
        dto.setChrgitmLv(entity.getChrgitmLv());
        dto.setLmtUsedFlag(entity.getLmtUsedFlag());
        dto.setBegndate(entity.getBegndate());
        dto.setEnddate(entity.getEnddate());
        dto.setValiFlag(entity.getValiFlag());
        dto.setPinyin(entity.getPinyin());
        dto.setSpec(entity.getSpec());
        dto.setDosform(entity.getDosform());
        dto.setAprvno(entity.getAprvno());
        dto.setProdentpName(entity.getProdentpName());
        dto.setSourceInfno(entity.getSourceInfno());
        dto.setUpdtTime(entity.getUpdtTime());
        return dto;
    }

    /**
     * 将目录下载游标实体映射为 DTO     *
     * @param entity 游标行；{@code null} 时返回 {@code null}
     * @return 游标 DTO {@code null} */
    @Override
    public CatalogDownloadCursorResult toDto(YbCatalogDownloadCursor entity) {
        if (entity == null) {
            return null;
        }
        CatalogDownloadCursorResult dto = new CatalogDownloadCursorResult();
        dto.setId(entity.getId());
        dto.setContractId(entity.getContractId());
        dto.setInfno(entity.getInfno());
        dto.setLocalVer(entity.getLocalVer());
        dto.setLastFileQuryNo(entity.getLastFileQuryNo());
        dto.setLastFilename(entity.getLastFilename());
        dto.setLastSuccessAt(entity.getLastSuccessAt());
        return dto;
    }

    /**
     * 1312 平台返回行批量转换为领域 upsert 命令     *
     * @param rows 平台输出；{@code null} 或空时返回空列表
     * @return upsert 命令列表（跳hilist_code 为空的行 */
    @Override
    public List<UpsertNationalCatalogRowCommand> toUpsertCommands(List<Cat1312RowOutput> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<UpsertNationalCatalogRowCommand> commands = new ArrayList<>(rows.size());
        for (Cat1312RowOutput row : rows) {
            if (row == null || isEmpty(row.getHilistCode())) {
                continue;
            }
            UpsertNationalCatalogRowCommand cmd = new UpsertNationalCatalogRowCommand();
            cmd.setListType(StringUtils.defaultIfBlank(row.getListType(), ListType.WESTERN_DRUG.getCode()));
            cmd.setHilistCode(row.getHilistCode());
            cmd.setHilistName(row.getHilistName());
            cmd.setChrgitmLv(row.getChrgitmLv());
            cmd.setLmtUsedFlag(row.getLmtUsedFlag());
            cmd.setBegndate(parseDate(row.getBegndate()));
            cmd.setEnddate(parseDate(row.getEnddate()));
            cmd.setValiFlag(row.getValiFlag());
            cmd.setPinyin(row.getPinyin());
            cmd.setSourceInfno(ChsInfNo.CATALOG_QUERY);
            cmd.setUpdtTime(parseDateTime(row.getUpdtTime()));
            cmd.setRawJson(JSON.toJSONString(row));
            commands.add(cmd);
        }
        return commands;
    }

    /**
     * 解析平台日期字段（yyyy-MM-dd 或更长字符串取前 10 位）     *
     * @param value 平台日期      * @return 解析成功返回 {@link LocalDate}，否则 {@code null} */
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
     * 解析平台 updt_time 等日期时间字段      *
     * @param value 平台时间     * @return 解析成功返回 {@link LocalDateTime}，否则 {@code null} */
    private static LocalDateTime parseDateTime(String value) {
        return ChsDateTimeParseUtil.parseLocalDateTime(value);
    }
}
