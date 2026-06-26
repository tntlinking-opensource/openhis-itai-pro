/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.chs.application.catalog.mapper;

import com.alibaba.fastjson2.JSON;
import com.openhis.chs.api.ChsInfNo;
import com.openhis.chs.api.dto.catalog.Catalog1312RowOutput;
import com.openhis.chs.domain.catalog.dto.response.CatalogDownloadCursorDto;
import com.openhis.chs.domain.catalog.dto.response.NationalCatalogItemDto;
import com.openhis.chs.domain.catalog.command.UpsertNationalCatalogRowCommand;
import com.openhis.chs.domain.catalog.entity.YbCatalogDownloadCursor;
import com.openhis.chs.domain.catalog.entity.YbNationalCatalogItem;
import com.openhis.chs.domain.catalog.ChsCatalogConstants;
import com.openhis.chs.application.catalog.support.ChsDateTimeParseUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 国家目录 DTO / Command 映射实现（纯字段搬运与平台行解析，不承载业务流程判断）。
 */
@Component
public class NationalCatalogMapperImpl implements NationalCatalogMapper {

    /**
     * 将国家目录镜像实体映射为列表行 DTO。
     *
     * @param entity 目录镜像行；{@code null} 时返回 {@code null}
     * @return 列表行 DTO 或 {@code null}
     */
    @Override
    public NationalCatalogItemDto toDto(YbNationalCatalogItem entity) {
        if (entity == null) {
            return null;
        }
        NationalCatalogItemDto dto = new NationalCatalogItemDto();
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
     * 将目录下载游标实体映射为 DTO。
     *
     * @param entity 游标行；{@code null} 时返回 {@code null}
     * @return 游标 DTO 或 {@code null}
     */
    @Override
    public CatalogDownloadCursorDto toDto(YbCatalogDownloadCursor entity) {
        if (entity == null) {
            return null;
        }
        CatalogDownloadCursorDto dto = new CatalogDownloadCursorDto();
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
     * 将 1312 平台返回行批量转换为领域 upsert 命令。
     *
     * @param rows 平台输出；{@code null} 或空时返回空列表
     * @return upsert 命令列表（跳过 hilist_code 为空的行）
     */
    @Override
    public List<UpsertNationalCatalogRowCommand> toUpsertCommands(List<Catalog1312RowOutput> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<UpsertNationalCatalogRowCommand> commands = new ArrayList<>(rows.size());
        for (Catalog1312RowOutput row : rows) {
            if (row == null || StringUtils.isBlank(row.getHilistCode())) {
                continue;
            }
            UpsertNationalCatalogRowCommand cmd = new UpsertNationalCatalogRowCommand();
            cmd.setListType(StringUtils.defaultIfBlank(row.getListType(), ChsCatalogConstants.DEFAULT_LIST_TYPE));
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
     * 解析平台日期字段（yyyy-MM-dd 或更长字符串取前 10 位）。
     *
     * @param value 平台日期串
     * @return 解析成功返回 {@link LocalDate}，否则 {@code null}
     */
    private static LocalDate parseDate(String value) {
        if (StringUtils.isBlank(value)) {
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
     * 解析平台 updt_time 等日期时间字段。
     *
     * @param value 平台时间串
     * @return 解析成功返回 {@link LocalDateTime}，否则 {@code null}
     */
    private static LocalDateTime parseDateTime(String value) {
        return ChsDateTimeParseUtil.parseLocalDateTime(value);
    }
}
