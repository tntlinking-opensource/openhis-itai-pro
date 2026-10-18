# PEIS 现场需求与缺口清单

> 来源：现场琐碎记录整理（个检 / 团检 / 检中 / 报告 / 日结）  
> 对照代码：`zxplat/health-application/.../peis` + `physicalexam/financial`  
> 评估日期：2026-08-19  
> 整体覆盖粗估：**约 55%～65%**（个检主路径约 70%）  
> 用途：后续按缺口慢慢开发，不必一次做完

---

## 1. 需求整理（现场口径）

### 1.1 个人体检 · 检前

1. 健康查体登记台
2. 新增个人体检
3. 个人信息和套餐（可改价；支付只要支持现金）
4. 费用管理 → 开发票

### 1.2 团体体检 · 检前

1. 单位信息
2. 单位登记：套餐和人员（分组、套餐；支持人员调整；当年不交钱）
3. 套餐暂时不收
4. 单位收费（暂时不用）
5. 要支持动态调整体检项目
6. 单位约 10 人录完以后有套餐，有可能出现空白套餐项目（需能兜住，不能直接卡死）

### 1.3 检中（个人 / 团体通用）

1. 无论个人还是团体：都在健康体检登记台登记、签到、打指引单
2. 分检台录入单项结果
   - 能自动带的带
   - 彩超没有 PACS 截图
   - 电诊需要支持常用取值
   - 尿常规有的值不回写（现场痛点）
3. 总检
   - 看看有没有丢项
   - 24 小时内写完
   - 异常结果、疾病和总检内容要支持绑定
   - 总检保存后，在护士站-个人报告处能查到报告

### 1.4 其它

1. 要支持日结
2. 团体体检报告

---

## 2. 现网能力一览

| 业务 | AppService / 前端 |
|------|-------------------|
| 个检登记 | `PeisPersonalRegistrationAppService` / `views/peis/personal-register` |
| 团检管理 | `PeisGroupCheckAdminAppService` / `views/peis/group-check` |
| 个检收费开票 | `PhysicalExamChargeAppService`（`physicalexam/financial/charge`，不在 peis 包） |
| 团检收费骨架 | `PhysicalExamGroupChargeAppService` |
| 分检台 | `PeisDeptExamDeskAppService` / `views/peis/dept-desk` |
| 总检台 | `PeisMasterReviewDeskAppService` / `views/peis/master-review` |
| 个人报告 | `PeisReportPrintAppService` / `views/peis/report/print` |
| 报告发放 / 团打 | `PeisReportGrantAppService` / `views/peis/report/grant` |
| 目录 / 诊断 / 参数 | `config/{catalog,diagnosis,settings}` |

**额外已有、现场笔记未点名：** 复查、历年历史、健康档案、毕业合打、公告、Campus 体检类型推断、年度防重。

---

## 3. 需求点对照（已实现 / 部分 / 未实现）

图例：✅ 已实现 · ⚠️ 部分实现 · ❌ 未实现 · ⏸️ 暂不做（现场明确）

### 3.1 个人体检 · 检前

| # | 需求 | 状态 | 证据 / 说明 |
|---|------|------|-------------|
| 1 | 健康查体登记台 | ✅ | `PeisPersonalRegistrationAppService`：`searchCustomer` / `pagePersonalRegistrations`；菜单 `/peis/personal-register` |
| 2 | 新增个人体检 | ✅ | `createRegistration` → `PeisRegistrationOrchestrator.registerPersonalCheckIn`（建 PE Encounter + 套餐展开 SR） |
| 3 | 信息 + 套餐（可改价，只要现金） | ⚠️ | 有选患者、选套餐；**无改价字段**；渠道可含现金，前端默认偏微信 |
| 4 | 费用管理 / 开发票 | ⚠️ | `preSettleCharges` / `confirmChargeSettlement` + 开票编排；非独立费用管理台，改价/退费归门诊财务轴 |

### 3.2 团体体检 · 检前

| # | 需求 | 状态 | 证据 / 说明 |
|---|------|------|-------------|
| 1 | 单位信息 | ✅ | `saveCompany` / `listCompanies` |
| 2 | 单位登记（分组 / 套餐 / 人员调整 / 当年不交钱） | ✅ | 筹备 + 登记台 `lookupRosterForDesk`；`confirmBatchEnrollment`（ENROLLED）；教职工 Excel 导入已支持，**Campus 同步**见 [02-教职工导入同步Campus方案](peis-import/02-教职工导入同步Campus方案.md)（GAP-PEIS-GRP-02）；缺按人换套餐 |
| 3 | 套餐暂时不收 | ✅ | P0-1 免收费建单；合同 `contract_amount` 单位后结 |
| 4 | 单位收费暂时不用 | ✅ | `markContractPaid` + 合同对账页；FIN-002 写侧已冻结 |
| 5 | 动态调整体检项目 | ✅ | 筹备名册「加/减项」+ `adjustRosterServiceRequests`；登记台亦可 |
| 6 | 空白套餐项目 | ✅ | 允许空子项建 Encounter；筹备加/减项 UI 已补 |

### 3.3 检中

| # | 需求 | 状态 | 证据 / 说明 |
|---|------|------|-------------|
| 1 | 登记 / 签到 / 打指引单 | ⚠️ | 登记台团检路径：建单/到检/指引单；签到仍依赖 booking |
| 2 | 分检录入 + 自动带回 + 彩超截图 + 常用取值 + 尿常规回写 | ⚠️ | 手工录入 / 常用结果有；`projectResults` **硬拒绝**（LIS/PACS Gap）；无彩超附件上传 |
| 3 | 总检丢项 / 24h / 疾病绑定 / 护士站可查 | ⚠️ | `assertDeptComplete` 拦未完成 SR，无丢项清单、无 24h、无 Condition 绑定；报告在 `/peis/report/print`，非护士站专页 |

### 3.4 其它

| # | 需求 | 状态 | 证据 / 说明 |
|---|------|------|-------------|
| 1 | 日结 | ⚠️ | 无 PEIS 专属日结；门诊日结仅有体检人次统计口径 |
| 2 | 团体体检报告 | ⚠️ | `batchGroupPrint` 仅 `markPrinted`，PDF 渲染待 PrintTemplate 接通 |

---

## 4. 建议开发优先级（可慢慢做）

### P0 — 现场跑不通就阻塞

| ID | 缺口 | 建议方向 | 关联代码 / 文档 |
|----|------|----------|-----------------|
| P0-1 | 团检免收费建单 | ✅ 2026-08-22 | `confirmBatchEnrollment` + `ENROLLED`；登记台主路径；AS-PEIS-GRP-001 v1.3 |
| P0-2 | 登记台签到 + 指引单 | 个人/团体统一：签到后打导检单/指引单 | 打印场景码、`PeisPrintScenarioConstants`（现仅报告） |
| P0-3 | 个人改价（现金） | 登记或收费侧支持议价，结算按议价金额 | `CreateRegistrationRequest`、`PhysicalExamPlannedChargeEnsureSupport` |

### P1 — 检中体验与数据质量

| ID | 缺口 | 建议方向 | 关联代码 / 文档 |
|----|------|----------|-----------------|
| P1-1 | LIS/PACS 结果投影 | 关闭 `projectResults` Gap；尿常规回写、能带的自动带 | `PeisDeptExamDeskAppService.projectResults`、`AS-PEIS-INT-001` |
| P1-2 | 彩超截图 / 影像附件 | 分科附件上传与报告引用 | DES-06（文档有、代码无） |
| P1-3 | 电诊常用取值 | 已有通用常见结果，按科室/项目补齐配置与 UI | `applyCommonResult`、`PeisDiagnosisConfigAppService` |
| P1-4 | 团检动态改项 / 人员调套餐 | ✅ 筹备加/减项；名册级换套餐仍待做 | `adjustRosterServiceRequests`、团检筹备 UI |
| P1-5 | 空白套餐项兜底 | ✅ 后端 + 筹备加/减项 UI | `PeisRegistrationOrchestrator` |
| P1-6 | 总检丢项清单 | 列出未检/未弃检项，而非只拒绝保存 | `assertDeptComplete` |
| P1-7 | 总检 24 小时 | 参数化时限提醒或校验 | `PeisModuleSettings` |
| P1-8 | 异常 / 疾病 / 总检绑定 | Condition / 自动诊断表达式落地 | `peis_auto_express`、`AS-PEIS-CFG-002` |

### P2 — 收尾与运营

| ID | 缺口 | 建议方向 | 关联代码 / 文档 |
|----|------|----------|-----------------|
| P2-1 | 护士站个人报告入口 | 护士站可查 PE 报告，或明确复用 `/peis/report/print` | 报告索引 `upsertFromPeis` |
| P2-2 | 团体报告出纸 | PrintTemplate 接通团体版式 | `PeisReportGrantAppService.batchGroupPrint` |
| P2-3 | 体检专属日结 | 个检真实 Payment 进日结；团检免收费策略明确是否计入 | 门诊日结轴 / 新建 PE 日结 |
| P2-4 | 分科弃检 / 延检 UI | 常量已有 `abandoned`/`deferred`，补接口与界面 | DES-05 |

---

## 5. 暂不做（现场已声明，勿提前做重）

- 团体套餐收费（金额恒 0 / 骨架即可）
- 单位真实收银（页面可留入口，不接真实 Payment）

开发时注意：团检「结算」若仅用于建单，命名/文案上要与真实收费区分，避免运营误用。

---

## 6. 验收口径（后续开发自检）

### 个检闭环

- [ ] 登记台新建 → 选套餐 → **改价** → **现金**收费 → 开票
- [ ] 签到 → **打指引单**
- [ ] 分检手工录入；常用结果可用
- [ ] 总检完成 → 个人报告可打印/可查

### 团检闭环（当年不交钱）

- [ ] 维护单位 → 合同/批次/名册
- [ ] **不收费**即可建单进分科
- [ ] 人员可调、套餐可换、项目可加减
- [ ] 空白套餐项可兜住或后补
- [ ] 与个检共用签到 / 指引单 / 分检 / 总检
- [ ] 团体报告可出纸（或至少可预览）

### 检中质量

- [ ] LIS/PACS 可投影；尿常规关键值回写
- [ ] 彩超可挂截图（若现场仍无 PACS）
- [ ] 总检可见丢项；异常/疾病可绑定总检结论
- [ ] 日结能统计体检收费（口径与产品确认）

---

## 7. 结论（给后续自己看）

- **现在能试跑：** 个检「建档登记 → 收费开票 → 手工分科 → 总检 → 打个人报告」
- **还不能当生产：** 团检免收费闭环、指引单、检验/影像自动回写、改价、体检日结、团体正式出纸
- 建议顺序：**P0-1 → P0-2 → P0-3 → P1-***，P2 按运营催促再开

---

## 8. 变更记录

| 日期 | 说明 |
|------|------|
| 2026-08-19 | 初稿：现场需求整理 + peis 覆盖度对照 + P0/P1/P2 缺口清单 |
