# Flyway 脚本 Baseline Squash 方案

> **状态**：已执行（分支 `feat/flyway-baseline-squash`，2026-07）  
> **范围**：`zxplat` 全部 Flyway 迁移脚本（约 150 个 `.sql` → squash 后约 34 个）  
> **前提**：系统尚未正式发布，可对迁移历史做一次性 baseline 压缩  

**关联文档**：

- 现行 health 规范：[`zxplat-domain/.../db/migration/health/README.md`](../zxplat/zxplat-domain/src/main/resources/db/migration/health/README.md)
- booking 模块说明：[`module-booking-application/.../booking/README.md`](../../../模块/zxplat-module-booking/module-booking-application/src/main/resources/db/migration/booking/README.md)
- 多模块 Flyway 配置：[`ModularFlywayConfiguration.java`](../zxplat/zxplat-domain/src/main/java/com/openhis/health/infrastructure/config/ModularFlywayConfiguration.java)

---

## 1. 背景与问题

### 1.1 现状概览

| 模块 | 路径 | 脚本数 | history 表 |
|------|------|--------|------------|
| **health 核心** | `zxplat-domain/.../db/migration/health/` | 102 (V1–V102) | `flyway_schema_history` |
| booking | `module-booking-application/.../booking/` | 13 | `flyway_schema_history_booking` |
| chs | `module-chs-jilin-starter/.../chs/` | 12 | `flyway_schema_history_chs` |
| emr | `module-emr-application/.../emr/` | 6 | `flyway_schema_history_emr` |
| elep | `module-elep-jilin-application/.../elep/` | 3 | `flyway_schema_history_elep` |
| einvoice-boss | `module-einv-boss-application/.../einvoice_boss/` | 2 | `flyway_schema_history_einvoice_boss` |
| unionpay-bpc | `module-bpc-application/.../unionpay_bpc/` | 3 | `flyway_schema_history_unionpay_bpc` |
| crb | `module-crb-application/.../crb/` | 1 | `flyway_schema_history_crb` |
| work-injury | `module-wi-jilin-starter/.../wi/` | 3 | `flyway_schema_history_wi` |
| project-H22010402403 | `whale-project/.../project-H22010402403/` | 2 | `flyway_schema_history_project_H22010402403` |

架构：**同一 PostgreSQL 库、health 核心先跑、扩展模块按 `zxplat.modules.*.enabled` 链式执行**，各模块独立 history 表。

### 1.2 主要痛点

1. **`V1__Init.sql` 过大**（约 4400+ 行）：`whale_*` 建表 + 大量种子 + 菜单演进 UPDATE 链 + 已合并 V77–V98 增量 seed。
2. **版本号曾重排**：文件名与文件头注释不一致（如 V83 文件头写 V84）。
3. **命名不统一**：health 用 `adm_patient`；booking 用 `Booking_*`；unionpay 用 `UnionPayBpc_*`。
4. **非「建表 + 种子」语句散落**：ALTER / UPDATE / DELETE / 临时函数等（见 §4）。
5. **种子未分层**：health-app 开发演示数据与院点生产必要数据混在同一套脚本。
6. **院点特化数据位置不当**：如 einvoice V2 朝阳对照应在 project，不应在模块 baseline。

---

## 2. 已拍板决策（执行约束）

| # | 决策 | 说明 |
|---|------|------|
| D1 | **方案二：发版 Baseline Squash** | 首次发布前将开发期多版本迁移压缩为干净 baseline |
| D2 | **全部模块启用** | health-app / project 的 `application.yml` 已全开 chs/booking/emr 等 |
| D3 | **`wkf_*` 归属 health** | booking 只保留 `bkg_*` DDL/种子；`wkf_*` 全部在 health baseline |
| D4 | **health-app 加载全量种子** | 含演示用户、组织、药品、排班等，供开发联调 |
| D5 | **院点不要演示数据** | project 启动仅 platform 必要种子 + admin |
| D6 | **院点只要必要种子** | 术语 Concept、角色权限框架、菜单导航、CHS 契约占位等 |
| D7 | **院点仅 admin 用户** | 不含 yaofang/kuguan/门诊医生等演示账号 |
| D8 | **测试数据不进院点 project** | 演示/联调数据放 health-dev；院点 project 只放院点差异 |
| D9 | **SQL 只允许建表 + 种子** | UPDATE/ALTER/DELETE/临时函数等全部整合进 CREATE 或 INSERT 终态（见 §3） |

### 2.1 问答拍板记录（2026-07-12）

| # | 问题 | 结论 |
|---|------|------|
| Q1 | platform `ter_concept` 范围 | **全量平台术语**（与现 V50/V51 相当） |
| Q2 | 院点组织/科室 seed | **仅一个根节点**（如「本院」）；无科室/药房子节点 |
| Q3 | 菜单/权限 seed 位置 | **混合**：框架菜单 → health V10；模块菜单 → 各模块 seed |
| Q4 | `fin_contract` | **platform 契约占位**（bus_no 对齐配置，凭证空/占位） |
| Q5 | EMR seed | **全部 platform**（文档类型 + 元数据 + 默认模板） |
| Q6 | 打印 seed | **platform**：`prt_print` 场景码/空壳；**dev**：V102 完整 template |
| Q7 | MRMS seed | **全部 platform**（MRMS 非独立 Flyway 模块，不进模块 jar） |
| Q8 | health DDL 粒度 | **9 个域文件** V1–V9 + V10 seed + V99 sequence |
| Q9 | dev seed 开关 | **`openhis.flyway.dev-seed.enabled`** + health-app 独立 classpath |
| Q10 | einvoice 朝阳对照 | **对照仅 project**：`einv_charge_contrast`（YLZY*）→ project seed；模块 V2 只保留菜单/权限（与 D8 一致） |
| Q11 | project `SCAN_PAY` id | **9477005**（与 pay_enum 序列对齐）；合并 project V1/V2 |
| Q12 | 旧库迁移 | **一律 DROP SCHEMA 重建**，不兼容旧 history |
| Q13 | admin 密码 | **保留现 V1 BCrypt 哈希** |
| Q14 | 发版后增量 | **只允许新表 CREATE + INSERT**；已有表变更等下次 squash |
| Q15 | `老系统中药库/*.sql` | **保持原位**（人工参考，不参与 Flyway） |
| Q16 | 业务主数据 | **全部 dev**（药品/收费/设备/供应商/患者/库存等） |
| Q17 | PEIS seed | **全部 dev**（platform 只有空表） |
| Q18 | booking 原 V1 wkf 注释 | **删除**；wkf 注释并入 health V6 |

---

## 3. SQL 内容规范（白名单）

### 3.1 允许出现的语句

| 类别 | 语句 | 说明 |
|------|------|------|
| **建表（DDL）** | `CREATE TABLE IF NOT EXISTS` | 含终态全部列 |
| | `CREATE INDEX IF NOT EXISTS` | 含唯一索引、部分索引 |
| | `COMMENT ON TABLE / COLUMN` | 与 health README 要求一致 |
| **种子** | `INSERT INTO ... VALUES ...` | 推荐 `ON CONFLICT DO NOTHING` |
| | `INSERT ... ON CONFLICT DO UPDATE` | 视为幂等种子（菜单、术语 upsert） |

### 3.2 禁止独立出现（squash 时吸收）

| 禁止 | 整合方式 |
|------|----------|
| `ALTER TABLE ... ADD COLUMN` | 列并入对应 `CREATE TABLE` |
| `UPDATE` | 终态写入 `INSERT`；空库不需要存量回填 |
| `DELETE` | 终态 seed 不包含被删行 |
| `CREATE OR REPLACE FUNCTION` + 动态 SQL | 改为显式 `INSERT INTO whale_sequence` |
| 存量数据清洗（去重、回填） | 空库 baseline 直接删除整段 |

### 3.3 每个文件的标准结构

```sql
-- ========== DDL ==========
CREATE TABLE IF NOT EXISTS ...;
CREATE INDEX IF NOT EXISTS ...;
COMMENT ON TABLE ...;
COMMENT ON COLUMN ...;

-- ========== SEED（可选；platform/dev 分文件）==========
INSERT INTO ... VALUES ... ON CONFLICT DO NOTHING;
```

### 3.4 发版后增量约定

| 场景 | 推荐做法 |
|------|----------|
| 新表 | 新文件仅 `CREATE TABLE` + 可选 `INSERT` |
| 已有表加列 | 优先等下次 squash 并入 CREATE；短期可单文件 `ALTER` 并标注「待 squash 吸收」 |
| 改种子/菜单 | 新 `INSERT ... ON CONFLICT DO UPDATE`，禁止单独 `UPDATE` |

### 3.5 CI 门禁（可选，squash 完成后启用）

```bash
rg '^\s*(UPDATE|ALTER|DELETE|DROP)\b' zxplat/**/db/migration/**/*.sql \
  --glob '!**/_templates/**' --glob '!**/_RESET.sql'
# 预期：0 matches（ON CONFLICT 的 INSERT 不受限）
```

---

## 4. 待整合的非标脚本清单

全库扫描结果（Flyway 目录内，不含 `_templates`）：

| 文件 | 问题 | 整合目标 |
|------|------|----------|
| `health/V1__Init.sql` | ~164 UPDATE、3 DELETE、83 ON CONFLICT 演进链 | 终态 menu/permission → 纯 INSERT platform seed |
| `health/V99__whale_sequence_seed_sync.sql` | 临时函数 + 动态 SELECT | 显式 `INSERT whale_sequence`（platform/dev 各一份或分版本） |
| `health/V102__prt_print_template_seed.sql` | UPDATE 旧模板 + INSERT 新模板 | dev seed：INSERT 写终态 `print_json` |
| `booking/V10__Outpatient_Booking_Dedup.sql` | ALTER + UPDATE 去重 + 唯一索引 | 列/索引 → health `wkf_appointment_queue` CREATE；清洗逻辑删除 |
| `booking/V12__Schedule_Template_Lineage.sql` | ALTER `wkf_schedule` 加 2 列 | 并入 health `wkf_schedule` CREATE |
| `booking/V13__...Demo_Schedule.sql` | UPDATE 模板有效期 | dev seed INSERT 直接写最终日期 |
| `chs/V11__chs_settlement_schema_align.sql` | 新表 + 61 处 ALTER ADD COLUMN | 新表保留 CREATE；列并入 V5/V6 等原建表 |
| `chs/V12__yb_invoke_log_infname_err_msg.sql` | ALTER 加 2 列 | 并入 `yb_invoke_log` CREATE |
| `unionpay/V2__UnionPayBpc_AuditSoftDelete.sql` | ALTER 加 deleted_at/by | 并入 `upc_bpc_order` V1 CREATE |

**不在 Flyway 扫描范围、保持原位的文件**：

- `module-booking/.../_templates/booking/_TEMPLATE.sql`、`_RESET.sql`（运维模板）
- `whale-project/.../老系统中药库/*.sql`（遗留导出，应移出 `db/migration` 或归档到 `docs/legacy-sql/`）

---

## 5. ALTER → CREATE TABLE 合并对照表

squash 时须手工核对以下增量列已写入终态 CREATE：

| 来源脚本 | 目标表 | 新增列 / 索引 |
|----------|--------|----------------|
| `booking/V10` | `wkf_appointment_queue` | `healthcare_service_id`, `booking_date`；唯一索引 `uk_wkf_appt_patient_service_day` |
| `booking/V12` | `wkf_schedule` | `source_schedule_template_id`, `slot_provision_template_id`；索引 `idx_wkf_schedule_source_tpl` |
| `chs/V11` §2 | `yb_clinic_pre_settle` | `mdtrt_cert_type`, `mdtrt_cert_no`, `insutype`, `acct_used_flag`, `psn_setlway`, `fulamt_ownpay_amt`, `overlmt_selfpay`, `preselfpay_amt`, `inscp_scp_amt` |
| `chs/V11` §3 | `yb_inpatient_pre_settle` | 同上 + `insuplc_admdvs` |
| `chs/V11` §4 | `yb_inpatient_fee_upload` | `chrgitm_lv`, `med_chrgitm_type`, `selfpay_prop`, `fulamt_ownpay_amt`, `overlmt_amt`, `preselfpay_amt`, `inscp_scp_amt`, `lmt_used_flag`, `platform_memo` |
| `chs/V11` §5 | `yb_settlement_record` | `settle_mode`, `psn_name`, `insutype`, …（约 30 列，见原 V11） |
| `chs/V11` §6 | `yb_settlement_reversal` | `invoke_log_id` |
| `chs/V11` §1 | （新表） | `yb_invoke_log`, `yb_settlement_fund_detail`, `yb_settlement_correction` |
| `chs/V12` | `yb_invoke_log` | `infname`, `err_msg` |
| `unionpay/V2` | `upc_bpc_order` | `deleted_at`, `deleted_by` |

---

## 6. 目标目录结构

### 6.1 health 核心（`zxplat-domain`，全环境 classpath）

```
db/migration/health/
├── V1__whale_platform.sql          # CREATE whale_*（无 seed）
├── V2__adm_ddl.sql                 # CREATE adm_*
├── V3__cln_ddl.sql                 # CREATE cln_* + dia_*
├── V4__fin_ddl.sql                 # CREATE fin_* + inventory_lock
├── V5__med_ddl.sql                 # CREATE med_*
├── V6__wkf_ddl.sql                 # CREATE wkf_*（含 V10/V12 已合并列）
├── V7__ter_cus_ddl.sql             # CREATE ter_* + cus_* + doc_*
├── （V8 已迁出）                   # peis_* → ModularFlyway `db/migration/peis/V1__peis_ddl.sql`
├── V9__mrms_prt_ddl.sql            # CREATE mrms_* + prt_* + whale_qrtz 等
├── V10__seed_platform.sql          # INSERT：platform 必要种子（§7.1）
└── V99__seed_whale_sequence.sql    # INSERT whale_sequence（必须最后）
```

### 6.2 health-app 专属（仅开发环境 classpath）

```
health-app/src/main/resources/db/migration/health-dev/
├── V11__seed_dev_users.sql
├── V12__seed_dev_org_location.sql
├── V13__seed_dev_practitioner.sql
├── V14__seed_dev_charge_catalog.sql
├── V15__seed_dev_inventory.sql
├── V16__seed_dev_clinical_sample.sql
├── V17__seed_dev_peis_sample.sql
├── V18__seed_dev_booking_demo.sql      # 原 booking V8/V13 演示排班
├── ...
└── V98__seed_dev_prt_templates.sql     # 原 V102 打印模板
```

**配置开关**（`ModularFlywayConfiguration` 扩展）：

```yaml
# health-app/application.yml
openhis:
  flyway:
    dev-seed:
      enabled: true
      locations: classpath:db/migration/health-dev

# project/application.yml
openhis:
  flyway:
    dev-seed:
      enabled: false
```

**执行顺序**：

- health-app：`V1…V10` → `V11…V98`（dev）→ `V99`（sequence）
- project：`V1…V10` → `V99`（无 dev 文件在 classpath 上）

### 6.3 扩展模块（squash 后）

| 模块 | baseline 文件 | 种子归属 |
|------|---------------|----------|
| **booking** | `V1__bkg_ddl.sql`（V2–V7 合并，无 wkf CREATE） | platform: `V2__seed_platform_menus.sql`（原 V6） |
| **chs** | `V1__chs_ddl.sql`（V1–V12 全合并） | 无 seed |
| **emr** | `V1__emr_ddl.sql` | platform: 文档类型/元数据定义；dev: 模板样例 |
| **elep** | `V1__elep_ddl.sql` | 无 |
| **einvoice-boss** | `V1__einv_ddl.sql` + `V2__seed_platform_menus.sql` | 模块：**仅**菜单/权限（原 V2 拆分）；**YLZY 对照 → project**（Q10） |
| **unionpay-bpc** | `V1__bpc_ddl.sql` + `V2__seed_platform_menus.sql` | 院点支付能力 → project |
| **crb / wi** | `V1__*_ddl.sql` | 无 |

### 6.4 院点 project

```
project-H22010402403/db/migration/project-H22010402403/
└── V1__project_H22010402403.sql    # INSERT only：SCAN_PAY、einv_charge_contrast（朝阳 YLZY*）、unionpay 开关等
```

合并并去重现有 `V1__ter_concept_scan_pay.sql` 与 `V2__UnionPayBpc_ScanPayEnable.sql`（二者均插 SCAN_PAY，id 不同）。

---

## 7. 种子三层分类

### 7.1 Platform Seed（`V10__seed_platform.sql`）

**health-app 与院点 project 均执行。**

| 类别 | 表 | 内容 |
|------|-----|------|
| 身份 | `whale_users` | **仅 admin** 一条 |
| 角色权限 | `whale_roles`, `whale_user_roles`, `whale_permissions`, `whale_permission_grants` | admin 角色 + 全部已启用模块的菜单/导航权限（终态 INSERT） |
| 租户 | `whale_tenants`, `whale_user_tenants`, `whale_settings` | 默认 `tenant_id=1` |
| 术语 | `ter_code_system`, `ter_concept` | **全量**平台枚举（Q1）；**不含**院点特有能力项（如 SCAN_PAY → project） |
| 组织 | `whale_organization_units`, `adm_organization`, `adm_location` | **仅根节点「本院」**（Q2）；无科室子树 |
| 医务人员 | `adm_practitioner` | **仅 admin 一条**（`user_id=1`，代码硬引用 V23） |
| 医保 | `fin_contract` | CHS 契约占位（`bus_no` 与配置键对齐，凭证可空） |
| 病案 | `mrms_qc_rule` | `tenant_id=0` 全局默认规则 |
| EMR | `emr_document_type`, `emr_metadata_field_def`, `emr_template` 等 | **全部 platform**（Q5：含默认模板） |
| MRMS | `mrms_qc_rule`, `mrms_required_doc_type`, mrms 菜单等 | **全部 platform**（Q7；非独立模块） |
| 打印 | `prt_print`、场景壳 | platform 场景码/空壳（Q6）；完整 JSON → dev |
| Quartz | `whale_qrtz_locks` | 框架必需 |

**从现 V1 剥离**：7 个演示用户、V77–V98 incremental menu block、所有中间 UPDATE/DELETE 菜单演进链。

### 7.2 Dev Seed（`health-dev/`，仅 health-app）

| 类别 | 现版本来源 | 内容 |
|------|------------|------|
| 演示账号 | V1 users, V23 practitioner | yaofang/kuguan/门诊医生/护士等 |
| 组织树 | V15–V16, V1 org seed | 完整科室/药房/收费处 |
| 主数据 | V4/V5 charge/device seeds | 收费项、设备目录（Q16：platform 无） |
| 药品库存 | V47, V57 | 药品定义 + 库存批次（Q16） |
| 排班预约 | booking V8, V13 | 模板 + wkf_schedule/slot 演示 |
| 体检 | V78–V84 | PEIS 全部演示数据（Q17） |
| 打印模板 | V102 | 完整 prt_template JSON（Q6） |
| 其他 | V1 `-- seed sync from DB` 块 | 从现网导出的样例行 |

### 7.3 Project Seed（院点 project jar）

| 放入 | 示例 | 不放 |
|------|------|------|
| 院点术语 | `SCAN_PAY`（477 pay_enum） | 演示用户/排班/库存 |
| 院点对照 | `einv_charge_contrast`（YLZY* / YLMZ*） | **仅 project**（Q10）；模块 baseline 不含院点收费对照 |
| 院点开关 | unionpay 扫码启用 | booking 演示 schedule 等不进 project |
| 院点机构 | （可选）真实 org 根 | 测试患者/就诊 |

---

## 8. 现有 health V1–V102 → squash 映射

| 目标文件 | 合并来源（现 V 号） |
|----------|---------------------|
| `V1__whale_platform.sql` | V1 中 `whale_*` DDL；V68 login_session；V70 qrtz |
| `V2__adm_ddl.sql` | V2–V23 adm_*（**DDL only**） |
| `V3__cln_ddl.sql` | V24–V28, V41, V69, V74–V76, V82 |
| `V4__fin_ddl.sql` | V42–V45, V46, V71–V72 |
| `V5__med_ddl.sql` | V47–V49 |
| `V6__wkf_ddl.sql` | V52–V66 + booking V10/V12 列/索引 |
| `V7__ter_cus_ddl.sql` | V29–V40, V50–V51, V77, V85, V100–V101 |
| ~~`V8__peis_ddl.sql`~~ → **`peis/V1__peis_ddl.sql`** | 原 V78–V84；ModularFlyway `flyway_schema_history_peis` |
| `V9__mrms_prt_ddl.sql` | V73, V86–V97 表结构 |
| `V10__seed_platform.sql` | V1 框架 seed（精简）+ 术语 + fin_contract + mrms_qc_rule + 菜单终态 + admin practitioner |
| `V99__seed_whale_sequence.sql` | 原 V99（改为显式 INSERT） |
| **health-dev/** | V1 演示用户 + V4/V5/V15/V16/V23 + V57 + peis + V98 mrms_menu + V102 prt + booking V8/V13 |

---

## 9. 执行计划（建议分 PR）

### Phase 0：拍板（本文档）

- [ ] 技术负责人确认 §2 决策与 §3 SQL 规范
- [ ] 确认 squash 后 health 约 11 个文件 + 各模块 1–2 个文件的可接受性
- [ ] 确认 dev-seed 双 classpath 方案

### Phase 1：终态导出（PR-1 前置，可不提 PR）

1. 空库 + 现脚本全模块 migrate
2. `pg_dump --schema-only` → 按域拆分 DDL 草稿
3. 关键表 data-only dump → 分拣 platform / dev / project
4. 对照 §4、§5 核对 ALTER 已并入 CREATE

### Phase 2：health baseline（PR-1）

1. 新建 V1–V9 DDL 文件，删除旧 V2–V102
2. 编写 `V10__seed_platform.sql`（admin-only + 术语 + 菜单终态）
3. 编写 `V99__seed_whale_sequence.sql`（显式 INSERT）
4. 更新 `health/.../README.md`
5. 空库验证：project classpath 模拟（无 dev-seed）

### Phase 3：health-dev + 配置（PR-2）

1. `health-app/.../health-dev/` 迁入演示 seed
2. 扩展 `ModularFlywayConfiguration` + `application.yml` 开关
3. 空库验证：health-app 全量启动 + 关键联调路径

### Phase 4：扩展模块 + project（PR-3）

1. 各模块 squash 为 V1（+ V2 seed 如有）
2. **einvoice**：拆分原 V2 → 模块 `V2__seed_platform_menus.sql`（菜单/权限）+ project `einv_charge_contrast`（朝阳对照）
3. booking 删除原 V1 wkf 注释；chs 合并 V11/V12
4. project 合并 V1/V2 为单文件（SCAN_PAY id=9477005 + einvoice 对照）
5. 移出 `老系统中药库/*.sql` 至 legacy 目录（Q15：**保持原位**，本步取消）

### Phase 5：门禁与文档（PR-4）

1. CI grep 门禁（§3.5）
2. 各模块 README 版本索引更新
3. （可选）`scripts/squash-flyway-release.mjs` 自动化脚本

---

## 10. 验证标准（Definition of Done）

### 10.1 院点（project）空库

- [ ] 可登录：admin / 默认密码
- [ ] 各 enabled 模块菜单入口可见
- [ ] 下拉枚举正常（`ter_concept`）
- [ ] **无**演示患者、排班、库存、多角色用户
- [ ] `fin_contract` CHS 占位存在
- [ ] `SCAN_PAY` 等来自 project flyway
- [ ] `V99` sequence 无冲突，新增 bus_no 不撞 seed

### 10.2 health-app 空库

- [ ] 上述 platform 检查通过
- [ ] 多角色账号可登录
- [ ] 有组织树、药品、库存、排班演示
- [ ] 集成测试 / 手工挂号台路径可走通

### 10.3 SQL 规范

- [ ] `db/migration/**` 无独立 `UPDATE`/`ALTER`/`DELETE`/`DROP FUNCTION`（§3.5）
- [ ] 每表每列有 `COMMENT ON`

### 10.4 删库重建

按现 README 要求，版本重排后必须：

```sql
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
GRANT ALL ON SCHEMA public TO CURRENT_USER;
GRANT ALL ON SCHEMA public TO public;
-- 扩展模块 history 表随 public schema 一并清除
```

---

## 11. 风险与缓解

| 风险 | 缓解 |
|------|------|
| squash 漏合 ALTER 列，实体与库不一致 | 空库 migrate 后跑集成测试；对比 JPA 实体字段 |
| platform seed 菜单不全，院点缺入口 | 从现网终态导出 menu/permission，人工 diff |
| `whale_sequence` 预计算错误 | 对照 dev seed 最大 bus_no 逐表校验 |
| 开发者本地库有旧 history | 文档强调 DROP SCHEMA；提供一键 reset 脚本 |
| Java 注释仍引用旧 `V23` 等版本号 | squash PR 顺带更新常量类 Javadoc（非阻塞） |
| 新院点无 einvoice 对照 | project 模板含 `einv_charge_contrast` INSERT；或对照页维护；纯 health-app 无 project 时对照表为空 |
| 发版后禁止 ALTER（Q14） | 加列只能等下次 squash 或建新表迁移数据 |

---

## 12. 拍板签字栏

| 角色 | 姓名 | 日期 | 结论 |
|------|------|------|------|
| 技术负责人 | | | ☐ 批准 ☐ 修订后批准 ☐ 驳回 |
| DBA / 数据 | | | ☐ 批准 ☐ 修订后批准 ☐ 驳回 |
| 实施负责人 | | | ☐ 批准 ☐ 修订后批准 ☐ 驳回 |

**修订意见**（如有）：

```
（在此填写）
```

---

## 附录 A：Flyway history 表清单

| history 表 | 模块 |
|------------|------|
| `flyway_schema_history` | health 核心 |
| `flyway_schema_history_booking` | booking |
| `flyway_schema_history_chs` | chs |
| `flyway_schema_history_emr` | emr |
| `flyway_schema_history_elep` | elep |
| `flyway_schema_history_einvoice_boss` | einvoice-boss |
| `flyway_schema_history_unionpay_bpc` | unionpay-bpc |
| `flyway_schema_history_crb` | crb |
| `flyway_schema_history_wi` | work-injury |
| `flyway_schema_history_project_H22010402403` | project |

## 附录 B：booking 与 health wkf 关系（拍板后）

- **health `V6__wkf_ddl.sql`**：全部 `wkf_*` 表 CREATE（终态列含 V10/V12）。
- **booking 原 V1（wkf 注释）**：**删除**（Q18）；注释并入 health V6。
- **booking `V1__bkg_ddl.sql`**：仅 `bkg_*` 表（原 V2–V7）；模块 seed 从 V2 起（如 `V2__seed_platform_menus.sql`）。

## 附录 C：V99 sequence 改写示例

```sql
-- V99__seed_whale_sequence.sql（platform 版，仅 admin 相关最小编号）
INSERT INTO whale_sequence (seq_key, current_value, updated_at)
VALUES
    ('1:AD', 1, CURRENT_TIMESTAMP),
    ('1:PR', 1, CURRENT_TIMESTAMP)
    -- ... 按 platform seed 实际最大 bus_no 预填
ON CONFLICT (seq_key) DO UPDATE
    SET current_value = GREATEST(whale_sequence.current_value, EXCLUDED.current_value),
        updated_at = CURRENT_TIMESTAMP;
```

dev 版在 `V98__seed_whale_sequence_dev.sql` 或使用更高 `current_value` 覆盖 platform 值（仍在 V99 之前执行 dev seed，V99 用 GREATEST 兜底）。

---

## 附录 D：einvoice V2 拆分（Q10 修订）

原 `einvoice_boss/V2__EinvoiceBoss_Seed.sql` squash 时拆为两部分：

| 内容 | 目标文件 | 执行环境 |
|------|----------|----------|
| `whale_permissions` / `whale_menus` / `whale_permission_grants`（EinvoiceBoss 菜单树） | `einvoice_boss/V2__seed_platform_menus.sql` | 启用 einvoice-boss 模块即跑 |
| `einv_charge_contrast`（YLZY01–12、YLMZ11、REG_FEE 等） | `project-H22010402403/V1__...sql` | 仅该院点 project 启用时跑 |

**注意**：

- 纯 `health-app`（无 project jar）启动时：`einv_charge_contrast` **为空**，电子发票对照需页面维护或挂 project。
- 新院点复制 `project-H22010402403` 模板时替换为该院 Boss 统计项编码，勿沿用朝阳 YLZY*。

---

*文档版本：2026-07-12 · 生成自 Flyway 整理讨论，执行前以仓库最新脚本为准复核 §4、§5。Q10 于同日修订为「对照仅 project」。*
