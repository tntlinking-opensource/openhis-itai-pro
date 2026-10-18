/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.mrms.caserecord.entity;

import com.zxplat.framework.core.domain.AuditedEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 病案首页重症监护行（{@code mrms_homepage_icu}）。
 *
 * <p>对齐老 {@code IN_HOS_ICUINFO}；4101A 节点 {@code icuinfo} 字段同名直出；
 * 4401 映射为 {@code icu_codeid}/{@code inpool_icu_time}/{@code out_icu_time}。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "mrms_homepage_icu")
public class HomepageIcu extends AuditedEntityBase {

    /** 所属病案主档 {@code mrms_case_record.id}。 */
    @Column(name = "case_record_id", nullable = false)
    private Long caseRecordId;

    /** 租户主键。 */
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /** 同页内排序号。 */
    @Column(name = "sort_no", nullable = false)
    private Integer sortNo;

    /** 重症监护病房类型（ICU_TYPE：1 CCU…7 ICU / 9 其他）。 */
    @Column(name = "scs_cutd_ward_type", length = 8)
    private String scsCutdWardType;

    /** 进入重症监护时间。 */
    @Column(name = "scs_cutd_inpool_time")
    private LocalDateTime scsCutdInpoolTime;

    /** 退出重症监护时间。 */
    @Column(name = "scs_cutd_exit_time")
    private LocalDateTime scsCutdExitTime;

    /** 合计时长（天/时/分，例 {@code 1/13/24}）。 */
    @Column(name = "scs_cutd_sum_dura", length = 16)
    private String scsCutdSumDura;

    /** 有效标记（默认 1）。 */
    @Column(name = "vali_flag", nullable = false, length = 3)
    private String valiFlag;
}
