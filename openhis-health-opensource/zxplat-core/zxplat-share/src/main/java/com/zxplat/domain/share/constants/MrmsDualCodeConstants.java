/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 病案国临码 / 医保码双码场景常量（展示、上传、打印策略锚点）。
 *
 * <p>西医：落库/编辑用国临；上传必须目录 {@code yb_no}（无对照报错，禁止国临顶替）；
 * 预览同屏国临+医保。中医：落库即医保目录码；预览医保列回显、国临列空；上传出站用该码。
 * 首页打印一律落库码。</p>
 */
public final class MrmsDualCodeConstants {

    /** 场景：结算清单预览同屏展示国临+医保。 */
    public static final String SCENE_SETTLE_PREVIEW = "settle_preview";

    /** 场景：4401 / 4101A 出站上传。 */
    public static final String SCENE_UPLOAD = "upload";

    /** 场景：病案首页打印。 */
    public static final String SCENE_HOMEPAGE_PRINT = "homepage_print";

    /** 预览未对码提示文案（上传将失败，禁止用国临顶替）。 */
    public static final String UNMAPPED_HINT = "未对码·不可上传";

    private MrmsDualCodeConstants() {
    }
}
