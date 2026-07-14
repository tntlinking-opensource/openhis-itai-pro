/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.domain.document.entity;

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
