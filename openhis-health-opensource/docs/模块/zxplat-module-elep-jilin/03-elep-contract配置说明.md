# elep-contract 配置说明

> **对齐真源**：`zxplat-module-chs-jilin/chs-jilin-application/src/main/resources/chs-contract.properties`  
> **加载器模式**：`ContractConfigLoader`（热加载、bus_no 后缀匹配、configEpoch）  
> **Whale 实现**：`ElepContractConfigLoader` + `ElepContractConfig`

---

## 1. 设计原则

1. **唯一配置源**：终端密钥、平台 URL、PDF 路径均在 properties；**不建** `yb_elep_contract_config`。
2. **段键 = `fin_contract.bus_no`**：与 CHS 一致；主数据表仅 `bus_no` 与 properties 对齐。
3. **热加载**：修改文件后自动生效（开发态监听 `target/classes`；生产外挂路径）。
4. **继承 CHS**：`fixmedins_code` / `fixmedins_name` / `adm_vs` 可在 elep 段省略，由 `ElepContractConfigResolver` 从同 `bus_no` 的 `chs-contract.properties` 补齐（`-Pwith-chs,with-elep` 同进程时）。

---

## 2. 解析顺序

1. 系统属性 `-Delep.contract.config.path=/path/to/elep-contract.properties`
2. 环境变量 `ELEP_CONTRACT_CONFIG_PATH`
3. classpath `elep-contract.properties` 对应本地文件（开发态 `target/classes`）
4. classpath 资源流（jar 内，仅启动时加载）

---

## 3. 键格式

```text
contract.{bus_no}.{field}
```

示例 `bus_no`：`H22010402403-229900`（与 CHS 段一致）。

若主数据 `bus_no` 仅为区划后缀（如 `229900`），加载器匹配以 `-229900` 结尾的键（同 CHS `ContractConfigLoader.getByBusNo`）。

---

## 4. 字段清单

### 4.1 机构与平台

| 键 | 必填 | 说明 |
|----|------|------|
| `contract_name` | 否 | 配置段显示名 |
| `fixmedins_code` | 是* | 定点机构编码；*可继承 CHS |
| `fixmedins_name` | 否 | 机构名称 |
| `adm_vs` | 是* | 统筹区；*可继承 CHS |
| `ele_base_url` | 是 | 电子处方中台根地址（与 A000x path 拼接） |
| `enabled` | 否 | 默认 `true`；`false` 时跳过该段 |

### 4.2 HSE 加解密（对应 ybapp `preAppId` 等）

| 键 | 说明 |
|----|------|
| `pre_app_id` | 预核验 AppId |
| `pre_app_secret` | 预核验 AppSecret |
| `pre_prv_key` | 预核验私钥 |
| `pre_pub_key` | 预核验公钥 |

### 4.3 OAuth（若平台需要，对应 ybapp `clientId` 等）

| 键 | 说明 |
|----|------|
| `client_id` | 客户端 ID |
| `client_secret` | 客户端密钥 |
| `username` | 平台账号 |
| `password` | 平台密码 |
| `scope` | 如 `server` |
| `grant_type` | 如 `password` |

### 4.4 PDF 与本地资源

| 键 | 说明 |
|----|------|
| `template_path` | PDF 模板绝对路径 |
| `output_path` | 生成 PDF 输出目录（按日期分子目录） |
| `hospital_seal_path` | 院章图片路径 |

### 4.5 运行参数

| 键 | 默认 | 说明 |
|----|------|------|
| `http_timeout_ms` | `40000` | HTTP 超时 |
| `catalog_cache_enabled` | `true` | 目录搜索缓存开关 |
| `catalog_cache_ttl_minutes` | `30` | L2 搜索结果 TTL |
| `catalog_search_cache_max_entries` | `5000` | L2 最大条目 |

### 4.6 不进配置（单次业务）

以下 ybapp `BaseInfo` 字段 **不得** 写入 properties，应从当次就诊/处方上下文读取：

- `dutyDoctorName`、`reason`、`medType`
- 患者证件、就诊凭证
- 处方明细、诊断内容

---

## 5. 示例文件

```properties
# 电子处方终端配置（OAuth、fixmedins、HSE 密钥、URL、PDF 等均在此文件）
# 配置段键 = fin_contract.bus_no（与 chs-contract 对齐）
# 热加载：
#   -Delep.contract.config.path=/path/to/elep-contract.properties
#   或环境变量 ELEP_CONTRACT_CONFIG_PATH
# 键格式：contract.{医疗机构编码}-{参保区划码}.*

# 吉林省医保（bus_no 建议：H22010402403-229900）
contract.H22010402403-229900.contract_name=吉林省电子处方
contract.H22010402403-229900.ele_base_url=https://xxx.elep.example.gov.cn
contract.H22010402403-229900.pre_app_id=
contract.H22010402403-229900.pre_app_secret=
contract.H22010402403-229900.pre_prv_key=
contract.H22010402403-229900.pre_pub_key=
contract.H22010402403-229900.client_id=
contract.H22010402403-229900.client_secret=
contract.H22010402403-229900.username=
contract.H22010402403-229900.password=
contract.H22010402403-229900.scope=server
contract.H22010402403-229900.grant_type=password
contract.H22010402403-229900.fixmedins_code=H22010402403
contract.H22010402403-229900.fixmedins_name=长春市朝阳区中医院
contract.H22010402403-229900.adm_vs=229900
contract.H22010402403-229900.template_path=D:/elep/template/rx.pdf
contract.H22010402403-229900.output_path=D:/elep/output/
contract.H22010402403-229900.hospital_seal_path=D:/elep/seal.png
contract.H22010402403-229900.http_timeout_ms=40000
contract.H22010402403-229900.catalog_cache_enabled=true
contract.H22010402403-229900.catalog_cache_ttl_minutes=30
contract.H22010402403-229900.enabled=true
```

生产环境密钥 **禁止** 提交 Git；使用外挂 properties 或环境变量覆盖。

---

## 6. 与 contractId 的解析链

```text
前端 contractId
  → fin_contract.bus_no
  → ElepContractConfigLoader.getByBusNo(busNo)
  → ElepContractConfig（可选 inherit 自 CHS）
  → ElepGatewayClient 每次 invoke 携带 busNo + tenantId
```

---

## 7. configEpoch 与缓存失效

`ElepContractConfigLoader.getConfigEpoch()` 在热加载成功后递增。以下组件应订阅 epoch 变化并清空本地状态：

- HSE 会话缓存（若有）
- `ElepCatalogCacheManager` 中与 bus_no 绑定的条目
- 网关连接池自定义状态（若有）
