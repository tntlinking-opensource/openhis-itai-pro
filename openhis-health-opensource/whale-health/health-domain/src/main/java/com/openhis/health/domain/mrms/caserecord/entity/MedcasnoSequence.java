/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.caserecord.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 病案号年度流水序列表（{@code mrms_medcasno_sequence}），租户+自然年唯一。
 *
 * <p>由 {@link com.openhis.health.domain.mrms.caserecord.support.MedcasnoGenerator} 行锁发号，
 * 格式 {@code {yyyy}{5位流水}}。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "mrms_medcasno_sequence")
public class MedcasnoSequence extends BaseAuditedEntity {

    /** 发号自然年（yyyy，与 tenant_id 组成唯一键）。 */
    @Column(name = "year_value", nullable = false)
    private Integer yearValue;

    /** 当前年度已分配最大流水号（下一号为 last_seq + 1）。 */
    @Column(name = "last_seq", nullable = false)
    private Integer lastSeq;
}
