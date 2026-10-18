/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.advice;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 皮试药最新登记结果快照（ClinicalStatus code + 展示文案）。
 *
 * <p>读侧由 companion doctor-prescription 皮试检查 SR → {@code AllergyIntolerance} 装配；
 * 同一皮试药多条 companion 时取 {@code recordedDate} 最新一条（BR-IP-ORD-016）。</p>
 */
@Data
@Accessors(chain = true)
public class SkinTestOutcomeSnapshot {

    /** 临床状态持久化码（如 {@code active}=阳性、{@code inactive}=阴性）。 */
    private String outcomeCode;

    /** 临床状态展示文案（如 阳性 / 阴性）。 */
    private String outcomeText;
}
