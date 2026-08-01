/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.audit.queries;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 明细审核违规统计页查询条件。
 */
@Getter
@Setter
@Accessors(chain = true)
public class DetailAuditStatsPageQuery {

    private LocalDateTime createdFrom;
    private LocalDateTime createdTo;
    /** 医师编码精确 */
    private String drCodg;
    /** 医师姓名模糊 */
    private String drNameKeyword;
    private String deptCodg;
    private String trigScen;
    private String sevDeg;
    /** 末次反馈处置：1 继续 / 2 返回修改；空=不限 */
    private String dspoWay;
    /** 规则名/违规内容模糊 */
    private String keyword;
    private Integer pageNo;
    private Integer pageSize;
}
