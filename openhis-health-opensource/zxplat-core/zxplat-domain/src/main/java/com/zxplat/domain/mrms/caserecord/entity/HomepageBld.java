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

/**
 * 病案首页输血行（{@code mrms_homepage_bld}）。
 *
 * <p>对齐老 {@code IN_HOS_BLDINFO}；4101A 节点 {@code bldinfo} 直出品种/量/单位。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "mrms_homepage_bld")
public class HomepageBld extends AuditedEntityBase {

    /** 所属病案主档 {@code mrms_case_record.id}。 */
    @Column(name = "case_record_id", nullable = false)
    private Long caseRecordId;

    /** 租户主键。 */
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /** 同页内排序号。 */
    @Column(name = "sort_no", nullable = false)
    private Integer sortNo;

    /** 输血品种（字典 {@code bld_cat_code}）。 */
    @Column(name = "bld_cat", length = 8)
    private String bldCat;

    /** 输血量（文本落库，上传按数值契约）。 */
    @Column(name = "bld_amt", length = 16)
    private String bldAmt;

    /** 输血计量单位（如 ml、U）。 */
    @Column(name = "bld_unt", length = 8)
    private String bldUnt;

    /** 有效标记（默认 1）。 */
    @Column(name = "vali_flag", nullable = false, length = 3)
    private String valiFlag;
}
