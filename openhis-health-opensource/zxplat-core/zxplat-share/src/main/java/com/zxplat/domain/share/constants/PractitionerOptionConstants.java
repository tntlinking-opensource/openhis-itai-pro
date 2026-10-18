/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 医务人员业务选型下拉 / 岗位池扫描常量。
 *
 * <p>库存初始化与单据经手人、财务审批制单人等「一次装载在职人员」内部分页共用本口径，
 * 禁止在 AppService / Loader / Support 内再声明散落 {@code PAGE_SIZE}。</p>
 */
public interface PractitionerOptionConstants {

    /**
     * 在职人员全量扫描时的单页条数（库存经手人、财务审批制单人等内部分页）。
     */
    int OPTION_SCAN_PAGE_SIZE = 500;
}
