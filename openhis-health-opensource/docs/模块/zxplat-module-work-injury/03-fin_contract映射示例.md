# 工伤 fin_contract / account 映射示例

> **现场确认**：院内 `fixmedins_code` 与省医保 **`H22010402403`** 同套；**DLL 出站** **`JL2201992002`**（见 [14-院方确认决策.md](./14-院方确认决策.md) Q2）  
> **关联**：[17-Whale医保多方案扩展骨架](../zxplat-module-chs-jilin/17-Whale医保多方案扩展骨架.md) §2.3 | [01-接入架构决策](./01-接入架构决策.md)

---

## 1. 映射原则

```text
ContractCategory.WORKER_INSURANCE
        ↓ 默认
InsuranceSchemeCode.WORK_INJURY
        ↓ chs-contract.properties
contract.{bus_no}.scheme=WORK_INJURY
contract.{bus_no}.fixmedins_code=JL2201992002   ← DLL 出站（契约 properties）
# fin_contract 院内列仍可 H22010402403
# DLL 在 OpenHIS.Win native/workinjury/；Whale 无 bridge_url
```

| 规则 | 说明 |
|------|------|
| **独立 `fin_contract` 行** | 工伤与省医保各一行，禁止共用同一 `bus_no` |
| **`fixmedins_code` 可同值** | 院点确认与省医保定点编码一致；写入工伤配置段与省医保段 |
| **`bus_no` 必须可区分** | 建议 `{fixmedins_code}-GS` 或 `-WI` 后缀 |
| **独立 `fin_account`** | 工伤结算账户 `contract_no` 指向工伤 `fin_contract.id` |
| **路由键** | 收费 / invoke 只认 `account → contract → bus_no → scheme` |

### 映射优先级（与多方案骨架一致）

1. `fin_contract.insurance_scheme_code`（列扩展后，推荐显式 `WORK_INJURY`）
2. `chs-contract.properties` → `contract.{bus_no}.scheme=WORK_INJURY`
3. `ContractCategory.WORKER_INSURANCE` 默认 → `WORK_INJURY`

---

## 2. 双契约院点示例（朝阳区中医院）

假设院点已存在省医保契约 `H22010402403-229900`（见 `chs-contract.properties` 样例）。

### 2.1 fin_contract（主数据）

| 字段 | 吉林省医保 | 工伤 |
|------|------------|------|
| `id` | 1001 | 1002 |
| `bus_no` | `H22010402403-229900` | `H22010402403-GS` |
| `name` | 吉林省医保 | 工伤保险 |
| `category_enum` | `PROVINCIAL_INSURANCE` | `WORKER_INSURANCE` |
| `yb_flag` | true | true |
| `org_id` | 当前机构 | 当前机构 |
| **`fixmedins_code`** | 院内列 **`H22010402403`** | 院内列 **`H22010402403`** |
| **DLL 出站编码** | — | **`JL2201992002`**（properties / Win） |

### 2.2 fin_account（结算账户）

| 字段 | 省医保账户 | 工伤账户 |
|------|------------|----------|
| `id` | 2001 | 2002 |
| `name` | 省医保统筹 | 工伤统筹 |
| `contract_no` | 1001 | 1002 |
| `category_enum` | 医保类 | 工伤类 |

收费分组时：

- 省医保费用 → `OutpatientInsuranceChargeGrouper` 识别 `PROVINCIAL_INSURANCE` → `CHS_NATIONAL_JILIN`
- 工伤费用 → 识别 `WORKER_INSURANCE` → `WORK_INJURY`（**二期编码**）

### 2.3 chs-contract.properties（终端配置）

```properties
# ── 已有：吉林省医保（FSI HTTP）──
contract.H22010402403-229900.scheme=CHS_NATIONAL_JILIN
contract.H22010402403-229900.fixmedins_code=H22010402403
contract.H22010402403-229900.fixmedins_name=长春市朝阳区中医院
contract.H22010402403-229900.adm_vs=229900
contract.H22010402403-229900.call_service_url=http://ddjk.jlhs.gov.cn:20215/mbs/fmi/fsi/api/callFsiService/callService
# ... OAuth、国密等省略 ...

# ── 新增：工伤（纯 DLL · OpenHIS.Win 出站）──
contract.H22010402403-GS.scheme=WORK_INJURY
contract.H22010402403-GS.contract_name=工伤保险合同
contract.H22010402403-GS.fixmedins_code=JL2201992002
contract.H22010402403-GS.fixmedins_name=长春市朝阳区中医院
contract.H22010402403-GS.recer_sys_code=LNYTH
contract.H22010402403-GS.infver=V1.0
# 无 bridge_url / dll_path / token_url / call_service_url
# DLL：openhis-pro-win/native/workinjury/JLSiInterface.dll
# 调用链：web-ele → 127.0.0.1:5000/api/work-injury/invoke（见 07-OpenHIS.Win工伤本地桥接.md）
```

> **要点**：院内 `fin_contract.fixmedins_code` 两段 **可同为 H22010402403**；**Win invoke** 读 properties **`JL2201992002`**。`bus_no` 与 `scheme` **不同**，网关据此分流。

---

## 3. scheme_code 与路由

收费/invoke 路由键：`account → fin_contract → bus_no → scheme=WORK_INJURY`。

**不**创建 `yb_coordination`（库中无表；见 [11-客户端invoke会话-详细设计.md](./11-客户端invoke会话-详细设计.md)）。

---

## 4. 收费 UI / 费用性质下拉

医生站、收费处「费用性质」需同时展示：

| 显示名 | contract bus_no | 账户 |
|--------|-----------------|------|
| 吉林省医保 | H22010402403-229900 | 2001 |
| 工伤保险 | H22010402403-GS | 2002 |
| 自费 | … | … |

患者工伤就诊时选择 **工伤保险** 账户 → 后续读卡/结算全走 DLL 链。

---

## 5. 运维检查清单

| # | 检查项 | 期望 |
|---|--------|------|
| 1 | 工伤 `bus_no` 在 `fin_contract` 与 properties 一致 | `H22010402403-GS` |
| 2 | 院内 `fixmedins_code` 与省医保段相同 | `H22010402403` |
| 2b | properties / Win 出站编码 | **`JL2201992002`** |
| 3 | `recer_sys_code` | **`LNYTH`** |
| 4 | `scheme=WORK_INJURY` 已配置 | 非 `CHS_NATIONAL_JILIN` |
| 5 | 未将工伤设为 `default-contract-bus-no` | 避免进销存/目录误路由 |
| 6 | 业务窗口 OpenHIS.Win + `native/workinjury/`，`GET /api/work-injury/health` 通过 | `nativeReady=true` |
| 7 | 9001 签到后 `sign_no` 非空（Win 进程内） | 除签到外 invoke 可过 |

---

## 6. 代码改造触点（二期备忘）

| 位置 | 现状 | 目标 |
|------|------|------|
| `OutpatientInsuranceChargeGrouper` | 仅 `PROVINCIAL_INSURANCE` | 增加 `WORKER_INSURANCE` 分组 |
| `InsuranceOutpatientSettlementAppService` | `schemeCode=CHS_NATIONAL_JILIN` 写死 | 从 contract 解析 |
| `InsuranceContractResolver.findFirstInsuranceContract` | 首条非自费 | 多契约时禁止作默认回落 |
| `InsuranceGatewayClient` 实现 | 仅 `ChsHttpAccessor` | 省医保 HTTP；工伤 **客户端 invoke**，JVM 不调 DLL |
