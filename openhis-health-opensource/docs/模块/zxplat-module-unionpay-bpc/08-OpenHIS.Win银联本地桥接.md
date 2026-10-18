# OpenHIS.Win 银联本地桥接（通道 C · P0 真源）

> **文档性质**：**MIS-POS 定长 + posinf.dll** 在收银 PC 上的实现真源 — 替代 legacy `UnionPayPlugin`  
> **工程**：`openhis-pro-win/`（与读卡 `OpenHIS.Win.Chs`、打印同托盘）  
> **协议真源**：[04-银联商务MIS-POS系统标准接口文档-定长接口.md](./04-银联商务MIS-POS系统标准接口文档-定长接口.md)  
> **Whale 落账**：[01-概要设计.md](./01-概要设计.md) · [03-详细设计与编码指导.md](./03-详细设计与编码指导.md)  
> **院点（H22010402403）**：[长春朝阳区医院/00-功能清单.md](./长春朝阳区医院/00-功能清单.md)  
> **支付编码真源**：[01-概要设计](./01-概要设计.md)  
> **版本**：v1.2 | **日期**：2026-06-23 | **实施状态**：待开发

---

## 1. 定位

| 项 | 说明 |
| --- | --- |
| **是什么** | 收银 PC 上 **OpenHIS.Win.exe** 内的银联子模块，通过 **JNA/DllImport 调 `posinf.dll#bankall`**，对外暴露 **JSON HTTP** |
| **替代谁** | legacy **`UnionPayPlugin.exe`**（`:19994`）；**不**再单独部署银联插件 |
| **不做什么** | 不写 `fin_payment`、不编排 settle；DLL 出站与 **Whale `zxplat-module-unionpay-bpc`** 落账分离 |
| **为何不用通道 A** | 院点签约仅提供 **MIS 定长 DLL**（无 BPC HTTPS `request_url`/`md5_secret`）；见 [05-BPC接口对照表](./05-BPC接口对照表.md)（通道 A 为将来开通预留） |

### 1.1 三通道

见 [01-概要设计 §2.2](./01-概要设计.md) 与 [README](./README.md)。P0 朝阳走 **通道 C（本文）**。

---

## 2. 端到端流程（P0）

```text
1. web-ele chargeDialog 勾选「扫码支付」
2. Whale 预结算 → payment 草稿（DRAFT）+ 返回 paymentDraftId
3. （若医保）CHS confirm 成功 → 自付段 amount
4. Whale unionPayBpc#prepareConsumerScan → merTradeNo（幂等键）
5. web-ele POST http://127.0.0.1:5000/api/unionpay/consumer-scan
     → OpenHIS.Win.UnionPay → bankall(定长) → 银联云（DLL 内部 HTTPS）
6. 成功 → web-ele 调 health settle
     · paymentDetails 一行 SCAN_PAY + clientScanResult（Win 回传）
7. health AppService 内 Extension.applyClientScanResult
     → 写 upc_bpc_order SUCCESS + 行替换 WECHAT/ALIPAY/BANK_CARD → settleOutpatientPayment
8. BPC/DLL 失败 → 不 settle；可重扫或改现金 CASH
```

**半成功**：步骤 5 成功、步骤 7 settle 失败 → `upc_bpc_order=SUCCESS` + payment `DRAFT` → [01 §6.5](./01-概要设计.md) 补偿。

**退费（逻辑同事务）**：web-ele 先调 Win `/api/unionpay/refund`（R）→ 成功后再调 Whale 退费 IF；R 失败 **不**进入本地退费。跨进程无法 DB 与 DLL 同一物理事务，以 **「R 成功门控 + 本地单事务」** 为准。

---

## 3. 工程结构（目标）

```text
openhis-pro-win/
  OpenHIS.Win.UnionPay/              ← 新建（对齐 OpenHIS.Win.Chs）
    PosInfNative.cs                  DllImport bankall
    MisPosFixedLengthCodec.cs        定长入参/出参（04 + 现网插件行为）
    UnionPayDispatcher.cs            consumer-scan / query / refund
    UnionPaySettings.cs              native 目录、ini 路径
    UnionPayLogger.cs
  OpenHIS.Win/
    Http/HttpServer.cs               注册 /api/unionpay/*
  native/unionpay/                   发布时拷贝（verify-native.ps1 校验）
    posinf.dll
    ums-ssl-lib.dll                  等 comm.ini 依赖
    nopos/
      B2Cpos.ini                     商户/终端/APPID（自 UnionPayPlugin 迁移）
      MISIP.ini, Rsp.ini, ...
```

| 项目 | 目标框架 | 平台 |
| --- | --- | --- |
| OpenHIS.Win.UnionPay | .NET Framework 4.7.2 | **x86**（与 posinf.dll 位宽一致） |

---

## 4. HTTP API（英文 · JSON）

基址：`http://127.0.0.1:5000`（与 [openhis-pro-win/README.md](../../openhis-pro-win/README.md) 一致；`PluginSettings.Http旧命名（已废止） 可配）。

### 4.1 `POST /api/unionpay/consumer-scan`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `merTradeNo` | string | Y | Whale 下发的商户订单号 |
| `scanCode` | string | Y | 付款码 |
| `amountYuan` | string | Y | 元，两位小数 |
| `posNo` | string | N | 缺省读 `B2Cpos.ini` / tid |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | boolean | 定长返回码成功（对齐 respCode=00 语义） |
| `respCode` | string | 如 `00` |
| `respMsg` | string | 可读描述 |
| `tradeNo` | string | 银联/BPC 交易号（落 `upc_bpc_order.trade_no`） |
| `payType` | string | `WEIX` / `ZFBA` / `UPAY`（BPC 原值） |
| `merTradeNo` | string | 回显 |
| `txnAmtFen` | long | 实际金额（分） |

内部：组装定长 → `bankall` → 解析出参（迁移 [UnionPayPlugin MainForm.cs](./银联-接口插件_v1.0/UnionPayPlugin/src/UnionPayPlugin/MainForm.cs) 中 `Posinfdll` 逻辑，**禁止**新 API 使用 `jylx`/`cm`/`je` 等拼音键）。

### 4.2 `POST /api/unionpay/query-order`

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `merTradeNo` | Y | 原商户订单号 |
| `tradeNo` | N | 有则优先 |

用于被扫超时轮询（对应 tranType **G** / 定长查询交易类型，见 04 §2.3.4）。

### 4.3 `POST /api/unionpay/refund`

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `merTradeNo` | Y | 原 C 交易 merTradeNo |
| `tradeNo` | Y | 原 `trade_no` |
| `refundAmountYuan` | Y | 退货金额（元）；**须等于**原渠道实付（`paidAmtFen`，无则 `txnAmtFen`），见 [03 §3.11](./03-详细设计与编码指导.md) |
| `refundMerTradeNo` | N | 逆向 merTradeNo；缺省 Whale 生成 |

### 4.4 能力探测

`/api/hello` 或 `/api/data/capabilities` 增加：

```json
{ "unionpay": true, "nativeReady": true }
```

web-ele 无 `unionpay` 时不展示「扫码支付」。

---

## 5. 配置分工

| 层 | 位置 | 内容 |
| --- | --- | --- |
| **DLL 运行时** | `native/unionpay/nopos/B2Cpos.ini` 等 | mid、tid、APPID/APPKEY、银联云 URL（**院点实施维护**） |
| **Whale 业务** | `unionpay-bpc-contract.properties` | `contract.*.enabled`、`mid`/`tid`（merTradeNo/校验）、`mer_trade_no_rule`、`pos_no`、轮询超时 |
| **L0 开关** | `application.yml` | `zxplat.modules.unionpay-bpc.enabled` |

**P0 通道 C 不需要** `request_url`、`md5_secret`（属通道 A）。

朝阳院迁移：自 `project-H22010402403/UnionPayPlugin/nopos/` 拷贝至 `native/unionpay/nopos/`。

---

## 6. Whale 侧配合（zxplat-module-unionpay-bpc）

| 组件 | P0 通道 C 行为 |
| --- | --- |
| `UnionPayBpcAppService#prepareConsumerScan` | 生成 `merTradeNo`、写 `upc_bpc_order` PENDING |
| `ThirdPartyPaymentExtension#applyClientScanResult` | 校验 Win 回传 → 更新 SUCCESS/FAILED + payType |
| `BpcPayTypeMapper` | WEIX/ZFBA/UPAY → WECHAT/ALIPAY/BANK_CARD（`health-domain-shared`） |
| `BpcTransactionClient` | **通道 C 不调用**；保留给通道 A |
| settle 门控 | health AppService 内 `applyClientScanResult` 成功后再落库；DTO 见 [03 §3.10](./03-详细设计与编码指导.md#310-settle-挂接-dto定稿-2026-06-23) |

Extension **不**在服务端加载 DLL；**禁止** health-application import native 代码。

---

## 7. 部署

| 项 | 要求 |
| --- | --- |
| 收银 PC | Windows x86；安装 **OpenHIS.Win.exe**（含 unionpay native） |
| 自启动 | 与读卡/打印相同（托盘 + 开机启动） |
| 网络 | 收银 PC 可 HTTPS 出网（DLL → api-m2.chinaums.com 等） |
| 卸载 | 移除 **UnionPayPlugin** 服务/自启；端口 **19994** 释放 |
| 应用服务器 | **可为 Linux**；银联 DLL **不在**服务端 |

---

## 8. 任务 ID（03 引用）

| ID | 任务 |
| --- | --- |
| WIN-UP-01 | 新建 `OpenHIS.Win.UnionPay` 项目 |
| WIN-UP-02 | `PosInfNative` + `MisPosFixedLengthCodec` |
| WIN-UP-03 | `/api/unionpay/consumer-scan` |
| WIN-UP-04 | `/api/unionpay/query-order` |
| WIN-UP-05 | `/api/unionpay/refund` |
| WIN-UP-06 | `native/unionpay` + `verify-native.ps1` |
| WIN-UP-07 | `/api/hello` 能力位 `unionpay` |
| WIN-UP-08 | 从 H22010402403 UnionPayPlugin 迁移 ini 验收 |
| DEP-UP-01 | `applyClientScanResult` + `prepareConsumerScan` |
| DEP-UP-02 | web-ele 调 Win 再 settle（02 §1.2） |

---

## 9. 验收要点

| # | 场景 | 期望 |
| --- | --- | --- |
| AC-WIN-01 | 未启动 OpenHIS.Win | web-ele 不展示或提示安装本地客户端 |
| AC-WIN-02 | consumer-scan 成功 | Whale settle 后双存 payType |
| AC-WIN-03 | consumer-scan 失败 | 不 settle |
| AC-WIN-04 | refund 成功 → Whale 退费 | 本地 REFUNDED |
| AC-WIN-05 | refund 失败 | 不进入 Whale 退费 |
| AC-WIN-06 | 无 UnionPayPlugin | 19994 无监听；5000 正常 |

---

## 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.4 | 2026-09-22 | §4.3：refundAmountYuan=渠道实付 paidAmtFen（无则 txn）；与 HIS 下单分账 |
| v1.3 | 2026-08-01 | §4.3：refundAmountYuan 须等于原扫码段 |
| v1.2 | 2026-06-23 | settle 定稿：health AppService 内 applyClientScanResult；03 §3.10 DTO |
| v1.1 | 2026-06-23 | 对齐 ADR-PAY-001：BpcPayTypeMapper 语义码映射 |
| v1.0 | 2026-06-23 | 首版：通道 C 定稿；替代 UnionPayPlugin；与 zxplat-module-unionpay-bpc 分工 |
