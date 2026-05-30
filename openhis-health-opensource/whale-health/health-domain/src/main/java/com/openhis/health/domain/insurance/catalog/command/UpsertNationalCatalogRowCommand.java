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

package com.openhis.health.domain.insurance.catalog.command;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 国家目录镜像 upsert 单行入参（1312/1301 解析后写入域层）。
 */
@Data
public class UpsertNationalCatalogRowCommand {

    private String listType;
    private String hilistCode;
    private String hilistName;
    private String chrgitmLv;
    private String lmtUsedFlag;
    private LocalDate begndate;
    private LocalDate enddate;
    private String valiFlag;
    private String pinyin;
    private String sourceInfno;
    private LocalDateTime updtTime;
    private String rawJson;
}
