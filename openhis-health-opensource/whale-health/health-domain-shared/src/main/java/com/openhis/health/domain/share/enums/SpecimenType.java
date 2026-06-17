/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.share.enums;

import lombok.Getter;

/**
 * 标本种类枚举（对应 {@code adm_specimen_definition.type_enum}）。
 */
@Getter
public enum SpecimenType implements EnumInterface {
    /** 血液样本。 */
    BLOOD(0L, "BLOOD", "血液样本", "", 1L),
    /** 尿液样本。 */
    URINE(0L, "URINE", "尿液样本", "", 2L),
    /** 唾液样本。 */
    SALIVA(0L, "SALIVA", "唾液样本", "", 3L),
    /** 组织样本。 */
    TISSUE(0L, "TISSUE", "组织样本", "", 4L),
    /** 脑脊液样本。 */
    CSF(0L, "CSF", "脑脊液样本", "", 5L),
    /** 痰液样本。 */
    SPUTUM(0L, "SPUTUM", "痰液样本", "", 6L),
    /** 粪便样本。 */
    FECES(0L, "FECES", "粪便样本", "", 7L),
    /** 拭子样本。 */
    SWAB(0L, "SWAB", "拭子样本", "", 8L),
    /** 骨髓样本。 */
    BONE_MARROW(0L, "BONE_MARROW", "骨髓样本", "", 9L),
    /** 精液样本。 */
    SEMEN(0L, "SEMEN", "精液样本", "", 10L),
    /** 羊水样本。 */
    AMNIOTIC_FLUID(0L, "AMNIOTIC_FLUID", "羊水样本", "", 11L),
    /** 毛发样本。 */
    HAIR(0L, "HAIR", "毛发样本", "", 12L),
    /** 指甲样本。 */
    NAIL(0L, "NAIL", "指甲样本", "", 13L),
    /** 其他。 */
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
