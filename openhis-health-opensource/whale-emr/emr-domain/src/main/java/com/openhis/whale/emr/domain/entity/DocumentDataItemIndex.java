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
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 文书数据项名索引（可选）。表 emr_document_data_item_index。
 */
@Getter
@Setter
@Entity
@Table(name = "emr_document_data_item_index")
public class DocumentDataItemIndex extends BaseAuditedEntity {

    /**
     * 文书 ID
     */
    @Column(name = "document_id", nullable = false)
    private Long documentId;

    /**
     * 节点类型：region / section / dataElement
     */
    @Size(max = 16)
    @Column(name = "node_type", length = 16, nullable = false)
    private String nodeType;

    /**
     * 数据项名，对应节点 attrs.name
     */
    @Size(max = 128)
    @Column(name = "data_item_name", length = 128, nullable = false)
    private String dataItemName;
}

