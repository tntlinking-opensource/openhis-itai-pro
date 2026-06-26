/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.domain.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 病案号年度流水序列表。
 */
@Getter
@Setter
@Entity
@Table(name = "mrms_medcasno_sequence")
public class MedcasnoSequence extends BaseAuditedEntity {

    /** 发号年度（yyyy）。 */
    @Column(name = "year_value", nullable = false)
    private Integer yearValue;

    /** 当前年度已分配最大流水号。 */
    @Column(name = "last_seq", nullable = false)
    private Integer lastSeq;
}
