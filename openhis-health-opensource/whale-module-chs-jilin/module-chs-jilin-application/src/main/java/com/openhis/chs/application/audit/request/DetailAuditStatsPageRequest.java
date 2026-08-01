/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.audit.request;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 事前提醒统计分页请求（{@code insuranceDetailAuditStats/pageHits}）。
 */
@Getter
@Setter
@Accessors(chain = true)
public class DetailAuditStatsPageRequest {

    /** 创建时间起（含）；必填 */
    private LocalDateTime createdFrom;
    /** 创建时间止（含）；必填 */
    private LocalDateTime createdTo;
    /** 责任医师编码（精确） */
    private String drCodg;
    /** 责任医师姓名关键字（模糊） */
    private String drNameKeyword;
    /** 科室编码（精确） */
    private String deptCodg;
    /** 触发场景 trig_scen */
    private String trigScen;
    /** 严重程度 sev_deg */
    private String sevDeg;
    /** 末次处置方式：1 继续 / 2 返回修改；空=不限 */
    private String dspoWay;
    /** 规则名/违规内容关键字 */
    private String keyword;
    /** 页码（从 1 起） */
    private Integer pageNo;
    /** 每页条数 */
    private Integer pageSize;
}
