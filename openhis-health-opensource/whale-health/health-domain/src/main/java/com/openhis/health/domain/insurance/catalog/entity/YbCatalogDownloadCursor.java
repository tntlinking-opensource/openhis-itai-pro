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

package com.openhis.health.domain.insurance.catalog.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 医保目录下载游标（1301/1305 等文件型接口）。
 */
@Getter
@Setter
@Entity
@Table(name = "yb_catalog_download_cursor")
public class YbCatalogDownloadCursor extends BaseAuditedEntity {

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "infno", nullable = false, length = 16)
    private String infno;

    @Column(name = "local_ver", nullable = false, length = 20)
    private String localVer = "0";

    @Column(name = "last_file_qury_no", length = 40)
    private String lastFileQuryNo;

    @Column(name = "last_filename", length = 200)
    private String lastFilename;

    @Column(name = "last_success_at")
    private LocalDateTime lastSuccessAt;
}
