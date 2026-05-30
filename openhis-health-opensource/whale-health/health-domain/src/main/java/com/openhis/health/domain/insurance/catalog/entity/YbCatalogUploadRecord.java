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

/**
 * 3301/3302 目录对照上传审计（C3 使用，C1 建表占位）。
 */
@Getter
@Setter
@Entity
@Table(name = "yb_catalog_upload_record")
public class YbCatalogUploadRecord extends BaseAuditedEntity {

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "operation", nullable = false, length = 8)
    private String operation;

    @Column(name = "product_table", nullable = false, length = 64)
    private String productTable;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "list_type", nullable = false, length = 30)
    private String listType;

    @Column(name = "fixmedins_hilist_id", nullable = false, length = 30)
    private String fixmedinsHilistId;

    @Column(name = "med_list_codg", nullable = false, length = 30)
    private String medListCodg;

    @Column(name = "platform_success", nullable = false)
    private Boolean platformSuccess;

    @Column(name = "msgid", length = 64)
    private String msgid;

    @Column(name = "request_snapshot", columnDefinition = "TEXT")
    private String requestSnapshot;

    @Column(name = "response_snapshot", columnDefinition = "TEXT")
    private String responseSnapshot;

    @Column(name = "error_msg", length = 512)
    private String errorMsg;

    @Column(name = "operator_id")
    private Long operatorId;
}
