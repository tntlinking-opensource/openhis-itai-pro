# HQMS 住院病案首页 CSV 导出 — 字段对照

> **渠道**：`upload.hqms`（与 `upload.medcas` / `upload.weistat` 平级，**独立管道**）  
> **规范真源**：《医院质量监测系统（HQMS）2.0 住院病案首页采集接口标准【2022版】》  
> **业务参照**：`guoji-jsp-his` `MainMRInterfaceController.exportCSV2` + `MainMRinterfaceServiceImpl.cvsExport_HQMS`（仅含义；成码以 ZXPlat 首页 + 本表为准）  
> **数据源**：病案首页表 + 费用快照（与 4401/清单同源 `fees`←落库列）  
> **诊断编码**：出站用**国临/ICD**；中医目录码不占出院主诊  
> **手术编码**：出站用首页 `oprnOprtCode`（**国临**）。**禁止**用诊疗目录 AD.`yb_no`。4401/4101A 出站才读同一条手术目录上的 `yb_no`（医保手术码；一行两码，不是诊断式对照）。  
> **版本**：v1.3 | **日期**：2026-10-07 | **院点启用**：国基 `H22010202493`

---

## 1. 架构口径

```text
HomepageAggregate（loadPage 同口径）
        └── HqmsRowAssembler → hqmsts_{机构名}.csv（811 列）
```

- **禁止**与卫统 N041 共用列 mapper。
- 文件名：`hqmsts_{医院名称}.csv`；编码 **GBK**；列分隔 `,`；行尾 `\r\n`。
- 部分 HQMS 必填空位写 `-`（对齐国基；与卫统「留空」策略不同）。
- 首页区划：`*_codes` 斜杠拼接；`birplc_name`/`napl_name` 中文地名（V25）。**A23C 不用地名列**，只用 `napl_codes`→RC036。

---

## 2. 列序真源（811）

列名顺序与国基 `cvsExport_HQMS` header 一致（`A01` … `D34`）。  
完整列清单见 `HqmsColumns.ORDERED`（代码真源；改列序须同步本文件）。

### 2.1 其他诊断槽

物理序 `C06x01C/C07x01N/C08x01C/F06x01` … 至 `x40`（共 40 组）。  
逻辑：`discharge_other` 按 `sortNo` 升序取前 40；主诊若由其他诊提升则去重后再写入。

### 2.2 手术槽

- **主要手术**：`C14x01C` … `C23x01`（无手术时编码/名称/日期写 `-`）
- **其他手术**：`C35x01C` … `C44x40`（最多 40；首页手术除主手术外按序填）
- `C14x01C` / `C35x*C`：首页 `oprnOprtCode`（国临）
- `C19x01`/`C20x01`、`C40x*`/`C41x*`：`asit1Name`/`asit2Name`；空写 `-`
- `C16x01`/`C37x*`：优先 `oprnTime`（`yyyy-MM-dd HH:mm:ss`）；仅有日期则 `yyyy-MM-dd 00:00:01`
- **仍空**：`F13`/`F14x*` 手术持续时间、`F15`/`F16x*` 麻醉分级（首页无源，与国基主手术 F13 常空一致）

---

## 3. 核心映射摘要（ZXPlat → HQMS）

| HQMS | ZXPlat | 转换 |
|------|-------|------|
| A01 | 医院 `medinsId`，否则 `ybNo` | 空→`-` |
| A02 | 医院 `name` | 原文 |
| A48 / A49 | `medcasno` / `patnIptCnt` | 次数空→`1` |
| B12 / B15 | `admTime` / `dscgTime` | `yyyy-MM-dd HH:mm:ss` |
| A47 | `healthCardNo` | 空→`-` |
| A46C | `payWayCode`（1～9） | `1→1.1` `2→2.1` `3→3.1`，其余原码；空→`9` |
| A11 / A12C / A13 / A14 | 姓名/性别/出生/年龄 | 出生 `yyyy-MM-dd` |
| A15C / A21C / A38C / A19C | 国籍/婚姻/职业/民族 | 本期直出院内码；职业空→`-` |
| A20N / A20 | 固定 `1` / `certno` | 尾号 X；TAB 防科学计数 |
| A22 | 优先 `birplcName`；空则 `birplcCodes` `/` 拼 | 出生地址字符项；中文地名符合接口 |
| A23C | `naplCodes` 首段区划码 → **RC036** | 接口必填值域 RC036（如吉林=`7`）；**禁止**出中文省名。映射见 `HqmsConstants#toRc036FromAdminDivisionCode` |
| A24～A28C | 户口/现住址/电话/邮编 | 码拼接+detail；空邮编等可 `-` |
| A29～A35 | 工作单位/联系人 | 空多用 `-`；关系空→`9` |
| B38 | — | 固定 `0`（非日间） |
| B11C | `admWayCode` | 直出 |
| B13C / B16C | 入/出院科别 | 机构 **`caty`**（同卫统；禁医保 A03） |
| B14 / B17 / B20 | 病房床位 / 住院日 | 拼接；日空→`1` |
| C01C / C02N | 门急诊西医码/名 | 国临 |
| F02C / F03N | `admission_main` | 入院主诊 |
| C03C / C04N / C05C / F05 | 出院主诊 | **西医优先**（同卫统规则） |
| C06x*…F06x* | `discharge_other` | §2.1 |
| C09C / C10N / C11 | 病理诊 + `pathologyNo` | |
| C12C / C13N | `injury_poison` | |
| C24C / C25 | 过敏有无/药名 | 空过敏→`1` |
| C26C / C27C | ABO / Rh | |
| B22～B33 | 医护质控姓名日期 | 姓名直出；编码列本期空 |
| C14x01… / C35x… | `procedures` | §2.2；码=国临；I助/II助出姓名；日期优先 `oprnTime` |
| B34C / B35 / B36C / B37 | 离院方式/转入机构/再住院 | |
| C28～C33 | 昏迷天时分 | 空→`0` |
| D01…D34 | `homepage.fees` | 空金额→`0`；刻度结算明文 |

---

## 4. 费用键对照（与卫统同源 fees）

| HQMS | fees key |
|------|----------|
| D01 | `medfee_sumamt` |
| D09 | `selfpay_amt` |
| D11 | `ordn_med_servfee` |
| D12 | `ordn_trt_oprt_fee` |
| D13 | `nurs_fee` |
| D14 | `com_med_serv_oth_fee` |
| D15 | `palg_diag_fee` |
| D16 | `lab_diag_fee` |
| D17 | `rdhy_diag_fee` |
| D18 | `clnc_dise_fee` |
| D19 | `nsrgtrt_item_fee` |
| D19x01 | `clnc_phys_trt_fee` |
| D20 | `rgtrt_trt_fee` |
| D20x01 | `anst_fee` |
| D20x02 | `rgtrt_fee` |
| D21 | `rhab_fee` |
| D22 | `tcm_trt_fee` |
| D23 | `wm_fee` |
| D23x01 | `abtl_medn_fee` |
| D24 | `tcmpat_fee` |
| D25 | `tcmherb_fee` |
| D26 | `blo_fee` |
| D27～D30 | 白蛋白/球蛋白/凝血/细胞因子 |
| D31～D33 | 一次性材料三类 |
| D34 | `oth_fee` |

---

## 5. 已知缺口

| # | 缺口 | 处理 |
|---|------|------|
| H1 | HQMS 专用字典（职业 RC003 / 民族 RC035 / 入院途径 RC026 / 麻醉 RC013 等） | 本期直出院内码；拒收再补对照。**A23C 已按 RC036 出站** |
| H2 | 医师执业证书编码（B22C…） | 空；可后续挂执业证号 |
| H3 | 手术 I助/II助 | **已关闭**：有名出姓名，空仍 `-` |
| H8 | 手术持续时间 / 麻醉分级（F13/F14/F15/F16） | 空；首页无持续小时、无麻醉分级码，不拿麻醉起止冒充手术时长 |
| H4 | ICU / 呼吸机 / 肿瘤等扩展段（F17…C50…） | 首页无源 → 空 |
| H5 | 科室未维护 `caty` | B13C/B16C 空 |
| H6 | A01 医院未配 medinsId/ybNo | `-` |
| H7 | 籍贯未选区划（`napl_codes` 空） | A23C 空；须补选籍贯后再导 |

---

## 6. IF / AppService

| 项 | 值 |
|----|-----|
| AppService | `MrmsHqmsExportAppService` |
| serviceName | `mrmsHqmsExport` |
| 方法 | `exportHqmsCsv` |
| Request | `caseRecordIds: Long[]` |
| Response | `fileName` / `contentType=text/csv;charset=GBK` / `body` / `exportedRowCount` |

Gate：主键非空；单批 ≤200；逐条 `loadPage`。

---

## 7. 前端 / 院点

- 主线关：`MRMS_HOMEPAGE_HQMS_EXPORT_ENABLED=false`
- 国基 override `true`；列表多选 +「导出HQMS CSV」
- API：`api/mrms/hqms-export.ts` → `POST /mrmsHqmsExport/exportHqmsCsv`

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.3 | 2026-10-07 | 手术码明确国临直出；I助/II助填首页一助/二助（关 H3）；日期优先 `oprnTime`；持续/麻醉分级仍空 |
| v1.2 | 2026-09-07 | A23C 按接口 RC036：区划码前两位→省码；不再出中文地名 |
| v1.1 | 2026-09-07 | A22/A23C 优先首页地名列 `birplc_name`/`napl_name` |
| v1.0 | 2026-09-07 | 首版：811 列管道 + 国基启用；与卫统分管道 |
