/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.healthcheckup.entity;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

/**
 * 体检号源时段模板（bkg_hc_slot_template）。
 *
 * <p>按机构记住上次模板配置，供一键生成未来日计划。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "bkg_hc_slot_template")
public class HcSlotTemplate extends BaseAuditedEntity {

    /** 机构标识。 */
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    /** 时段间隔分钟。 */
    @Column(name = "slot_minutes")
    private Integer slotMinutes;

    /** 上午开放开始时刻。 */
    @Column(name = "am_start")
    private LocalTime amStart;

    /** 上午开放结束时刻。 */
    @Column(name = "am_end")
    private LocalTime amEnd;

    /** 下午开放开始时刻。 */
    @Column(name = "pm_start")
    private LocalTime pmStart;

    /** 下午开放结束时刻。 */
    @Column(name = "pm_end")
    private LocalTime pmEnd;

    /** 各合同类型默认容量 JSON。 */
    @Column(name = "capacity_json")
    private String capacityJson;

    /** 模板休息日 ISO 1-7 逗号分隔。 */
    @Column(name = "rest_weekdays")
    private String restWeekdays;

    /** 生成时展开的校区编码列表 JSON。 */
    @Column(name = "campus_codes_json")
    private String campusCodesJson;

    /** 乐观锁版本号（非占号路径）。 */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;
}
