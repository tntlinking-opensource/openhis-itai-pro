# DOC-DRIFT 登记簿

> 文档↔代码暂时不一致时**必须**登记；关闭后把该行删掉或标 `已关闭`。  
> 规则：`.cursor/skills/文档代码一致性/SKILL.md`

| 日期 | 聚合/范围 | 模式 A/B | 漂移项 | 计划关闭 | 状态 |
|------|-----------|----------|--------|----------|------|
| 2026-09-23 | 10 个 stub DM | B | 已扩 **minimal canonical** 九章 + 方法附录 | 2026-09-24 | **已关闭**（`maturity: minimal`；升 full 另开） |
| 2026-09-23 | 全库旧路径 | — | `cmmi3ddd` / `zxplat` / `health/DomainModel` | 2026-09-23 | **已关闭** |
| 2026-09-23 | DomainService↔DM 方法白名单 | B | 全库附录 + 复跑 | 2026-09-23 | **已关闭**（Drift=0） |
| 2026-09-23 | QueryService 偏薄 | B | 附录 Query 表 | 2026-09-23 | **已关闭** |
| 2026-09-24 | L1 聚合说明铺开 | B | 非打样聚合 minimal L1；BC 页改索引 | 2026-09-24 | **已关闭** |
| 2026-09-24 | stub→full 九章细规 | A/B | `maturity: minimal` → `full`（`####` 方法条目） | 按需 | 开放 |
| 2026-09-24 | SRS 待迁 | — | 1 处「SRS 待迁」占位 | 文档 Wave | 开放 |
| 2026-09-24 | M33 miniapp AS-PEIS IF / 00 whale 残留 / Auth 路由 | B | AS 方法名对齐代码；清洗 `whale-*`；删幽灵 `deployment.mode` | 2026-09-24 | **已关闭** |
| 2026-09-24 | M34 pacs-imaging 包名/00–04/AS 断链/PortImpl | B | `plugin.pacsimaging`；无 bridge；PortImpl 落点；notify/mwl 配置 | 2026-09-24 | **已关闭** |
| 2026-09-24 | M33 DomainModel-SDD §4/§5.1 / PractitionerBinding 无独立 DM | B | AUTH SDD 字段+方法块；DM-MAP-004 + SDD；MAP-001～003 §4 | 2026-09-24 | **已关闭** |
| 2026-09-24 | M33/M34 L1 DomainService「关键写方法」表 | B | MapAuth / ImagingStudy / Media / ViewerEndpoint L1 补方法表 | 2026-09-24 | **已关闭** |
| 2026-10-07 | AS-IP-DOR-012 住院手术/麻醉开立 | B | 业务口径与代码已落地（`InpatientDoctorStationSurgeryAnesthAppService` / IF-IP-SUR-*）；**尚无正式 AS/AS-SDD 成稿**；对齐专题口径 v0.12：开立禁止附加；术前嘱托 basedOn 术式为唯一主子；麻醉平级无 basedOn；签发/撤回连带嵌套麻醉；**医嘱 Tab 排除挂术式术前嘱托（非全排 52）**；写正式 AS 时勿写 EXTRA/SURCHARGE 开立，勿写麻醉 basedOn。**2026-10-13 增量（仍欠正式 AS）**：Init 回传 `anesthetistOptions`（配置科室 bus_no→ROLE_DOCTOR 出诊岗）；麻醉申请/嵌套麻醉 **麻醉医师必填**；**仅开药**须落 `activity_id=0` 空壳 SR 作申请单锚点；删除/编辑旧 AR 先清同号麻醉药品草稿。**同日再增**：Init `doseUnitOptions`（术语 `dose_unit`）；麻醉药品 **单次剂量单位可检索改选**、Gate 必填；**不**按剂量单位换算总量 | 用户明示写 AS 时补全 | 开放 |
| 2026-10-08 | AS-IP-NUR-003/004 护士站类型列 | B | `orderKindDisplay` 目录类目回显 | — | **已关闭**（AS v1.25 / AS-SDD v1.3.19） |

明细：`plans/audit/pkg-doc-code-report.md` · 复跑：`pwsh tools/check-domain-doc-sync.ps1`
