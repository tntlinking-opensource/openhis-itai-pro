/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

import lombok.Getter;

/**
 * 标本种类枚举（对应 {@code adm_specimen_definition.type_enum}）。
 */
@Getter
public enum SpecimenType implements EnumInterface {
/** 血液样本（SpecimenType）。 */
    BLOOD(0L, "BLOOD", "血液样本", "", 1L),
/** 尿液样本（SpecimenType）。 */
    URINE(0L, "URINE", "尿液样本", "", 2L),
/** 唾液样本（SpecimenType）。 */
    SALIVA(0L, "SALIVA", "唾液样本", "", 3L),
/** 组织样本（SpecimenType）。 */
    TISSUE(0L, "TISSUE", "组织样本", "", 4L),
/** 脑脊液样本（SpecimenType）。 */
    CSF(0L, "CSF", "脑脊液样本", "", 5L),
/** 痰液样本（SpecimenType）。 */
    SPUTUM(0L, "SPUTUM", "痰液样本", "", 6L),
/** 粪便样本（SpecimenType）。 */
    FECES(0L, "FECES", "粪便样本", "", 7L),
/** 拭子样本（SpecimenType）。 */
    SWAB(0L, "SWAB", "拭子样本", "", 8L),
/** 骨髓样本（SpecimenType）。 */
    BONE_MARROW(0L, "BONE_MARROW", "骨髓样本", "", 9L),
/** 精液样本（SpecimenType）。 */
    SEMEN(0L, "SEMEN", "精液样本", "", 10L),
/** 羊水样本（SpecimenType）。 */
    AMNIOTIC_FLUID(0L, "AMNIOTIC_FLUID", "羊水样本", "", 11L),
/** 毛发样本（SpecimenType）。 */
    HAIR(0L, "HAIR", "毛发样本", "", 12L),
/** 指甲样本（SpecimenType）。 */
    NAIL(0L, "NAIL", "指甲样本", "", 13L),
/** 其他（SpecimenType）。 */
    OTHER(0L, "OTHER", "其他", "", 14L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    SpecimenType(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }
}
