/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inventory.entity;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 3505/3506 上报追溯码明细 */
@Getter
@Setter
@Entity
@Table(name = "yb_inv_upload_trace")
public class YbInvUploadTrace extends BaseAuditedEntity {

    @Column(name = "upload_id", nullable = false)
    private Long uploadId;

    @Column(name = "drug_trac_codg", nullable = false, length = 100)
    private String drugTracCodg;

    @Column(name = "trdn_flag", length = 3)
    private String trdnFlag;
}
