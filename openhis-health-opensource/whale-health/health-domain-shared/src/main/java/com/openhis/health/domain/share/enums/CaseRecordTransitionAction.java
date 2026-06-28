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

/**
 * 病案状态迁移动作（工作流入口）。
 */
public enum CaseRecordTransitionAction implements EnumInterface {
    /**
     * 创建主档。
     */
    CREATE(0L, "create", "创建", "", 0L),
    /**
     * 医生提交首页。
     */
    SUBMIT(0L, "submit", "提交", "", 1L),
    /**
     * 病案室接收。
     */
    RECEIVE(0L, "receive", "接收", "", 2L),
    /**
     * 退回医生修改。
     */
    RETURN(0L, "return", "退回", "", 3L),
    /**
     * 开始质控。
     */
    START_QC(0L, "startQc", "开始质控", "", 4L),
    /**
     * 归档。
     */
    ARCHIVE(0L, "archive", "归档", "", 5L),
    /**
     * 解档（高权限）。
     */
    UNARCHIVE(0L, "unarchive", "解档", "", 6L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举常量构造，绑定持久化 {@code code}、展示名与排序。
     *
     * @param codeSystemId 编码体系主键（预留）
     * @param code 持久化 code
     * @param display 界面展示名
     * @param definition 定义说明
     * @param sortOrder 排序号
     */
    CaseRecordTransitionAction(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    public Long getCodeSystemId() {
        return codeSystemId;
    }

    public String getCode() {
        return code;
    }

    public String getDisplay() {
        return display;
    }

    public String getDefinition() {
        return definition;
    }

    public Long getSortOrder() {
        return sortOrder;
    }
}
