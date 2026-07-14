/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.share.enums;

/**
 * 国家医保平台「字段字典」枚举的统一契约。
 *
 * <p>本包下枚举对应上传报文 JSON 里的<b>字段取值</b>（如 {@code inv_chg_type=106}），
 * 不是 Java 侧业务状态机。组装 1101/2207/3502 等交易时，应写 {@link #getCode()}，
 * 禁止在业务代码里散落 {@code "11"}、{@code "106"} 等魔法数字。</p>
 *
 * <p>{@link #getLabel()} 仅用于日志、运维界面展示，不参与平台校验。</p>
 *
 * <p>若 health 侧存在镜像枚举（如 {@code YbMedType}），变更本包枚举时须<b>人工同步</b>镜像
 * code 与中文文案（Skill §2.2.4）；禁止按中文 label 跨字段映射 code。</p>
 */
public interface ChsCodeEnum {

    /**
     * 平台认可的字段编码（与上传 JSON 值一字不差）。
     */
    String getCode();

    /**
     * 国家医保字典或局点文档中的中文名称。
     */
    String getLabel();
}
