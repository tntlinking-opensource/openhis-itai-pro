/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.prescriptionreview.queries;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 处方点评分页查询入参（DM-CUS-015 §5.2）。
 */
@Getter
@Setter
public class PrescriptionReviewPageQuery {

    /** 点评状态 code；{@code PENDING} 时读侧返回空页。 */
    private String statusEnum;
    /** 点评时间起（含）。 */
    private LocalDateTime reviewedAtFrom;
    /** 点评时间止（含）。 */
    private LocalDateTime reviewedAtTo;
    /** 是否合理。 */
    private Boolean reasonableFlag;
    /** 点评人。 */
    private Long reviewerId;
    /**
     * 关键字模糊：命中处方号或点评业务号其一即可（OR）。
     */
    private String keyword;

    /**
     * 开方时间窗预解析后的处方号集合（应用层写入）。
     *
     * <p>{@code null}=不按开方日收窄；空列表=窗内无方，分页空结果。</p>
     */
    private List<String> prescriptionNosIn;
}
