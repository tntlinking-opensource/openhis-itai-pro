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

package com.openhis.whale.emr.domain.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 文书版本快照。表 emr_document_version。
 */
@Getter
@Setter
@Entity
@Table(name = "emr_document_version")
public class DocumentVersion extends BaseAuditedEntity {

    /**
     * 文书 ID
     */
    @Column(name = "document_id", nullable = false)
    private Long documentId;

    /**
     * 版本号，同一文书内递增
     */
    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    /**
     * 版本内容 JSON
     */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;
}

