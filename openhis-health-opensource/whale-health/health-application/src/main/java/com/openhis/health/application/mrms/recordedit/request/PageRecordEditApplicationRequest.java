/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.recordedit.request;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 编辑审核列表分页（IF-MRMS-EDIT-05）。
 */
@Getter
@Setter
public class PageRecordEditApplicationRequest extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /**
     * document / homepage；空则分查两域后合并（本期合并为按页各查再拼接，前端宜分 Tab）。
     */
    private String targetKind;

    /** 状态过滤；空不限。 */
    private String statusEnum;
}
