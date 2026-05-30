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

import com.openhis.health.domain.share.constants.GeneralSharedConstants;

/**
 * 岗位业务角色编码（{@code adm_pr_assign.role_code}）。
 *
 * <p>与 DM-ADM-006 §4.1 RoleCode 一致；持久化列存整型 {@link #numericCode}。</p>
 */
public enum PractitionerBusinessRoleCode implements EnumInterface {

    DOCTOR(0L, "DOCTOR", "医师", "临床医师（含中医、西医、口腔等）", 1L, 1),
    NURSE(0L, "NURSE", "护士", "护理人员", 2L, 2),
    PHARMACIST(0L, "PHARMACIST", "药师", "药学人员", 3L, 3),
    WAREHOUSE_ADMIN(0L, "WAREHOUSE_ADMIN", "库管", "库房管理人员", 4L, 4),
    TECHNICIAN(0L, "TECHNICIAN", "技师", "检验/检查/影像等医技人员", 5L, 5),
    ADMINISTRATIVE(0L, "ADMINISTRATIVE", "行政", "行政管理人员（挂号员、收费员等）", 6L, 6);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;
    /** 与数据库 {@code role_code} 整型一致 */
    private final int numericCode;

    PractitionerBusinessRoleCode(
        Long codeSystemId,
        String code,
        String display,
        String definition,
        Long sortOrder,
        int numericCode) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
        this.numericCode = numericCode;
    }

    @Override
    public Long getCodeSystemId() {
        return codeSystemId;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getDisplay() {
        return display;
    }

    @Override
    public String getDefinition() {
        return definition;
    }

    @Override
    public Long getSortOrder() {
        return sortOrder;
    }

    public int getNumericCode() {
        return numericCode;
    }

    /**
     * 按库中整型解析枚举；无法识别时返回 {@code null}。
     */
    public static PractitionerBusinessRoleCode fromNumeric(Integer numericCode) {
        if (numericCode == null) {
            return null;
        }
        for (PractitionerBusinessRoleCode e : values()) {
            if (e.numericCode == numericCode) {
                return e;
            }
        }
        return null;
    }

    /**
     * 展示用中文名称；未知编码回退为 {@code "角色" + code}。
     *
     * @param numericCode 岗位业务角色整型编码
     * @return 展示名；编码为 null 时返回空串
     */
    public static String displayNameOf(Integer numericCode) {
        if (numericCode == null) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        PractitionerBusinessRoleCode e = fromNumeric(numericCode);
        if (e != null) {
            return e.getDisplay();
        }
        return "角色" + numericCode;
    }
}
