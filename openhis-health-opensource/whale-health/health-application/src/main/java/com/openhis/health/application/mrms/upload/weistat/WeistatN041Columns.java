/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.upload.weistat;

/**
 * 卫统 N041 CSV 列序真源（232 列，与国基 {@code cvsExport_N041} header 一致）。
 *
 * <p>文档：{@code whale-doc/health/MrmsAppService/11-卫统N041导出-字段对照.md}。
 * 改列序须同步文档与导出单测。</p>
 */
public final class WeistatN041Columns {

    /**
     * 其他诊断物理列交错逻辑槽序号（1-based）：写入 QTZD{i}/JBDM{i}/RYBQ{i} 的 i 顺序。
     */
    public static final int[] OTHER_DIAG_SLOT_ORDER = {
        8, 1, 9, 2, 10, 3, 11, 4, 12, 5, 13, 6, 14, 7, 15
    };

    /** 手术最大槽位数。 */
    public static final int MAX_PROCEDURE_SLOTS = 7;

    /** 其他诊断最大槽位数。 */
    public static final int MAX_OTHER_DIAG_SLOTS = 15;

    /**
     * N041 CSV 表头列名（严格顺序）。
     */
    public static final String[] ORDERED = {
        "USERNAME", "YLFKFS", "JKKH", "ZYCS", "BAH", "XM", "XB", "CSRQ", "NL", "GJ",
        "BZYZSNL", "XSECSTZ", "XSERYTZ", "CSD", "GG", "MZ", "SFZH", "ZY", "HY", "XZZ",
        "DH", "YB1", "HKDZ", "YB2", "GZDWJDZ", "DWDH", "YB3", "LXRXM", "GX", "DZ",
        "DH2", "RYTJ", "RYSJ", "RYSJS", "RYKB", "RYBF", "ZKKB", "CYSJ", "CYSJS", "CYKB",
        "CYBF", "SJZYTS", "MZZD", "JBBM", "ZYZD", "JBDM", "RYBQ", "QTZD8", "JBDM8", "RYBQ8",
        "QTZD1", "JBDM1", "RYBQ1", "QTZD9", "JBDM9", "RYBQ9", "QTZD2", "JBDM2", "RYBQ2",
        "QTZD10", "JBDM10", "RYBQ10", "QTZD3", "JBDM3", "RYBQ3", "QTZD11", "JBDM11", "RYBQ11",
        "QTZD4", "JBDM4", "RYBQ4", "QTZD12", "JBDM12", "RYBQ12", "QTZD5", "JBDM5", "RYBQ5",
        "QTZD13", "JBDM13", "RYBQ13", "QTZD6", "JBDM6", "RYBQ6", "QTZD14", "JBDM14", "RYBQ14",
        "QTZD7", "JBDM7", "RYBQ7", "QTZD15", "JBDM15", "RYBQ15", "WBYY", "H23", "BLZD",
        "JBMM", "BLH", "YWGM", "GMYW", "SWHZSJ", "XX", "RH", "KZR", "ZRYS", "ZZYS",
        "ZYYS", "ZRHS", "JXYS", "SXYS", "BMY", "BAZL", "ZKYS", "ZKHS", "ZKRQ",
        "SSJCZBM1", "SSJCZRQ1", "SSJB1", "SSJCZMC1", "SZ1", "YZ1", "EZ1", "QKDJ1", "QKYHLB1",
        "MZFS1", "MZYS1",
        "SSJCZBM2", "SSJCZRQ2", "SSJB2", "SSJCZMC2", "SZ2", "YZ2", "EZ2", "QKDJ2", "QKYHLB2",
        "MZFS2", "MZYS2",
        "SSJCZBM3", "SSJCZRQ3", "SSJB3", "SSJCZMC3", "SZ3", "YZ3", "EZ3", "QKDJ3", "QKYHLB3",
        "MZFS3", "MZYS3",
        "SSJCZBM4", "SSJCZRQ4", "SSJB4", "SSJCZMC4", "SZ4", "YZ4", "EZ4", "QKDJ4", "QKYHLB4",
        "MZFS4", "MZYS4",
        "SSJCZBM5", "SSJCZRQ5", "SSJB5", "SSJCZMC5", "SZ5", "YZ5", "EZ5", "QKDJ5", "QKYHLB5",
        "MZFS5", "MZYS5",
        "SSJCZBM6", "SSJCZRQ6", "SSJB6", "SSJCZMC6", "SZ6", "YZ6", "EZ6", "QKDJ6", "QKYHLB6",
        "MZFS6", "MZYS6",
        "SSJCZBM7", "SSJCZRQ7", "SSJB7", "SSJCZMC7", "SZ7", "YZ7", "EZ7", "QKDJ7", "QKYHLB7",
        "MZFS7", "MZYS7",
        "LYFS", "YZZY_YLJG", "WSY_YLJG", "SFZZYJH", "MD",
        "RYQ_T", "RYQ_XS", "RYQ_F", "RYH_T", "RYH_XS", "RYH_F",
        "ZFY", "ZFJE", "YLFUF", "ZLCZF", "HLF", "QTFY", "BLZDF", "SYSZDF", "YXXZDF", "LCZDXMF",
        "FSSZLXMF", "WLZLF", "SSZLF", "MAF", "SSF", "KFF", "ZYZLF", "XYF", "KJYWF", "ZCYF",
        "ZCYF1", "XF", "BDBLZPF", "QDBLZPF", "NXYZLZPF", "XBYZLZPF", "HCYYCLF", "YYCLF",
        "YCXYYCLF", "QTF"
    };

    /** 禁止实例化。 */
    private WeistatN041Columns() {
    }
}
