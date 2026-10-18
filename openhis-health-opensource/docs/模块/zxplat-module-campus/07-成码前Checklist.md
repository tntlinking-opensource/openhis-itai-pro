# Campus · 成码前 Checklist

> **版本**：v1.5 · **日期**：2026-07-19  
> **v1.5**：纳入 [ADR-CMP-001](./ADR-CMP-001-成码前决策.md)  
> **用途**：Campus 文档 → 代码会话 **门禁索引**；成码前逐项勾选。  
> **推荐首批**：ADR D2 = **C**（组织+档案+公费管理端；收银 D 仅 TODO）

---

## 0. 决策真源

| 项 | 文档 |
|----|------|
| 成码前决策 | **[ADR-CMP-001](./ADR-CMP-001-成码前决策.md)**（Accepted） |

要点：NENU `BENBU`/`JINGYUE`；独立模块 `-Pwith-campus`；V1 整包表；收银员选 `gfChargeMode`；甲乙丙分模式；自建审批；资格严+原因码；支付明细两行 + `PUBLIC_EXPENSE_ACCOUNT`（挂号对学生开户）。

---

## 1. 文档真源（须齐）

| 类别 | 路径 | 状态 |
|------|------|:--:|
| ADR | [ADR-CMP-001](./ADR-CMP-001-成码前决策.md) | ✅ |
| 功能清单 | [00-功能清单.md](./00-功能清单.md) | ✅ |
| 概要设计 | [01-概要设计.md](./01-概要设计.md) | ✅ |
| 交互说明 | [02-交互说明.md](./02-交互说明.md) | ✅ |
| AS 索引 | [03-AppService详细设计.md](./03-AppService详细设计.md) | ✅ |
| AS 契约 | [AppService/](./AppService/) | ✅ P0 |
| AS-SDD | [AppService-SDD/](./AppService-SDD/) | ✅ 组织/档案/CFG |
| 领域 DM | [DomainModel/](./DomainModel/) | ✅ |
| 领域 SDD | [DomainModel-SDD/](./DomainModel-SDD/) | ✅ |
| Flyway | [06](./06-Flyway-表结构.md) · [V1 SQL](./flyway/V1__campus_ddl.sql) | ✅ 草案 |
| Contracts | [08-Contracts与外部依赖.md](./08-Contracts与外部依赖.md) | ✅ |
| 文档清单 | [09-文档清单.md](./09-文档清单.md) | ✅ |

---

## 2. 成码批次

### Batch A — P0-1 组织（优先）

| 项 | 文档 | 成码包 |
|----|------|--------|
| Maven 骨架 | [模块说明-代码结构](./模块说明-代码结构.md) | campus-contracts/domain/application/adapter/starter |
| DM Site/College/Dept | DomainModel ORG-001～003 + SDD | `domain.org.*` |
| Flyway V1（整包，ADR D4） | flyway/V1 | campus-starter migration |
| AS ORG 三个 | AppService ORG-* | `application.org.*` |
| `CampusOrgQueryReader` | 08 | contracts + ReaderImpl |
| 校区种子 | ADR D1 | `BENBU`（主）/`JINGYUE` |

**完工定义**：见 00 §P0 组织条；Peis 可注入 Reader 列学院。

### Batch B — P0-2 档案

| 项 | 说明 | 状态 |
|----|------|:--:|
| PatientStudent + Staff + ChangeLog + Transfer + PE Profile | domain 已落 | ✅ |
| STU / STF / ARCH AppService | application 已落 | ✅ |
| Archive / Eligibility Reader | ReaderImpl 已落 | ✅ |
| ChargeCalcReader | stub（Batch C/D） | ⬜ |
| 学院/部门洗数 | GAP-CMP-MIG-01/02 | ⬜ |

### Batch C — P0-3 公费（管理端）

| 项 | 说明 | 状态 |
|----|------|:--:|
| GF 表 + CFG/RATIO AS | 甲乙丙分模式；CFG-02 弱化 | ✅ |
| APR | 自建状态机（不接 Flowable） | ✅ |
| CALC + ChargeCalcReader | 真实试算已落 | ✅ |
| health 预结算改草稿 | TODO 注释 + `gfChargeMode` 字段 | ⬜ D |
| 挂号开公费户 | TODO + `PUBLIC_EXPENSE_ACCOUNT` 枚举 | ⬜ D |

---

## 3. 合法 Gap

| Gap ID | 能力 | 关闭 |
|--------|------|------|
| GAP-CMP-MIG-01 | 学院三源洗数 | 迁移脚本 + COL |
| GAP-CMP-MIG-02 | work_unit → Department | DEP 导入 |
| GAP-CMP-MIG-03 | peis 学生 → `cmp_patient_student` | 洗数脚本 |
| GAP-CMP-CODE-01 | `zxplat-module-campus` 工程未建 | ✅ Batch A 已建 |
| GAP-CMP-UI-01 | `/campus/*` 页面 | ✅ 前端 + V3 菜单种子 |
| GAP-CMP-CHG-D | 预结算改支付明细 + 真实拆账 | 后续迭代 D |

~~GAP-CMP-ADM-01/02/03~~ 取消（health 已移除 `adm_patient_student`；学籍仅 campus）。

---

## 4. 成码会话提示（Batch A）

```
目标：创建 zxplat-module-campus 骨架 + 校区/学院/部门 P0
真源：ADR-CMP-001 + 07-成码前Checklist.md Batch A
约束：ModularFlyway campus；zxplat.modules.campus.enabled；
      -Pwith-campus；contracts 仅 Reader；不碰 peis_company_college 写路径
      种子校区 BENBU(主)/JINGYUE
验收：三 AS 可调；CampusOrgQueryReader.listActiveColleges 可用
```

---

## 5. 修订

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-07-19 | 首版门禁 |
| v1.1 | 2026-07-19 | old_peis 对齐 Gap（MIG/ADM-02）；Batch B 强调 card_no/工号 |
| v1.2 | 2026-07-19 | Batch B 含调岗/转院 Transfer |
| v1.4 | 2026-07-19 | health 已移除 ADM 学籍子表；关闭 GAP-CMP-ADM-03 |
| v1.3 | 2026-07-19 | 学籍表 `cmp_patient_student`；取消 ADM 扩列 Gap |
| v1.5 | 2026-07-19 | 纳入 ADR-CMP-001；Batch C 自建审批 + D TODO |
| v1.6 | 2026-07-19 | Batch A/B 代码已落；ChargeCalc stub |
| v1.7 | 2026-07-19 | Batch C 公费管理端+试算；health D TODO |
