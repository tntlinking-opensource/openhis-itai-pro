/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.platformdict.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import com.openhis.whale.core.domain.IExcludeFromAudit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 1901 平台字典镜像行；大批量同步，排除框架审计。 */
@Getter
@Setter
@Entity
@Table(name = "yb_platform_dict_item")
public class YbPlatformDictItem extends BaseAuditedEntity implements IExcludeFromAudit {

    @Column(name = "dict_type", nullable = false, length = 30)
    private String dictType;

    @Column(name = "dict_value", nullable = false, length = 20)
    private String dictValue;

    @Column(name = "dict_label", nullable = false, length = 200)
    private String dictLabel;

    @Column(name = "parent_value", length = 20)
    private String parentValue;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "vali_flag", length = 3)
    private String valiFlag;

    @Column(name = "platform_version")
    private Long platformVersion;

    @Column(name = "query_admdvs", length = 6)
    private String queryAdmdvs;

    @Column(name = "source_contract_id")
    private Long sourceContractId;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;
}
