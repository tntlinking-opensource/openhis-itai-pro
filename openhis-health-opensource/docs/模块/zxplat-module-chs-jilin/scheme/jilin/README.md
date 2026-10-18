# 吉林省医保对接说明（CHS_NATIONAL_JILIN）

> **方案码（HIS 侧）**：`InsuranceSchemeCode.CHS_NATIONAL_JILIN`（`fin_contract` / 账户维度，**非** chs-contract 配置项）  
> **架构真源**：[01-概要设计.md](../01-概要设计.md)  
> **实现状态**：[13-实现状态与文档对照.md](../13-实现状态与文档对照.md)

## 1. 范围

- 国家医疗保障信息平台定点基线版 **1.6.8**
- 吉林省本地化：**3203A–3209A**、**extData**、区划 **229900/220000**（结算 Wave 待编码）

## 2. 代码位置（当前）

| 层次 | 路径 |
|------|------|
| 网关 / DTO / yb_* | **`chs-jilin-domain`** `com.zxplat.plugin.chs.*` |
| AppService / 网关 HTTP | **`chs-jilin-application`** `com.zxplat.plugin.chs.application.*` |
| 区划规则 | `InsuplcAdmdvsRule`（非 `scheme.jilin` 包） |
| 目录 Assembler | `application.catalog.assembler.*` |
| HIS adapter | **`chs-jilin-bridge`** `integration.insurance.catalog.*` |

> **不存在**：`com.zxplat.plugin.chs.api.dto.scheme.jilin.*`、`application.gateway.scheme.jilin.*`（多方案包未在吉林省工程落地）。

## 3. 接口文档索引

| 类别 | 文档 |
|------|------|
| 报文约定 | [14-报文格式与约定.md](../14-报文格式与约定.md) |
| 目录 3301/3302 | [3301](../apis/33xx/3301-目录对照上传.md)、[3302](../apis/33xx/3302-目录对照撤销.md) |
| 门诊结算 | [2206](../apis/22xx/2206-门诊预结算.md)、[2207](../apis/22xx/2207-门诊结算.md) |
| 吉林清算 | [3203A](../apis/32xx/3203A-清算申请(吉林省).md) 等（apis 目录 **A** 后缀） |

## 4. 配置示例

```properties
# chs-contract.properties — 键为 fin_contract.bus_no，无 scheme 字段
contract.H22010402403.base_url=http://ddjk.jlhs.gov.cn:20215
contract.H22010402403.fixmedins_code=H22010402403
contract.H22010402403.adm_vs=229900
contract.H22010402403.client_id=...
```

## 5. 本地化要点（待结算 Wave）

- **extData**：规划 `JilinExtDataBuilder`（未编码）
- **insuplc_admdvs**：1101、3203A、3301、3302 等见 `InsuplcAdmdvsRule`
- **对账/清算**：`biz_type=RECON_CLEAR_JL`，infno 使用 A 变体
