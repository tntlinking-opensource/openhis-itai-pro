/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 挂号类型维护（基础数据）应用编排常量。
 */
public interface RegistrationTypeConstants {

    /** 作废服务项原因文案（写入领域命令）。 */
    String REASON_DELETE_BY_APP = "应用层删除挂号类型";

    /** 停用服务项原因文案。 */
    String REASON_DEACTIVATE_BY_APP = "应用层停用挂号类型";

    /** 诊查费候选项分页上限（维护弹窗一次加载）。 */
    int CONSULTATION_OPTION_PAGE_SIZE = 500;
}
