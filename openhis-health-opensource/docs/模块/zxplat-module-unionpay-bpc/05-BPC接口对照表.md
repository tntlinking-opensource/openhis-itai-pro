# Whale UnionPay-BPC 接口对照表

> **文档性质**：**通道 A** BPC HTTPS JSON 报文 — tranType、字段、respCode（**P0 不用**）  
> **P0 通道 C**：[08](./08-OpenHIS.Win银联本地桥接.md) + [04 定长接口](./04-银联商务MIS-POS系统标准接口文档-定长接口.md)  
> **版本**：v1.3 | **日期**：2026-06-23  
> **支付编码真源**：[01-概要设计](./01-概要设计.md)

---

## 1. 通信概要

```text
POST {contract.*.request_url}
Content-Type: application/json
Header:
  sign = MD5( jsonBody + "&" + md5SharedSecret )   // 小写 hex，无 0x 前缀

Body: JSON 对象（字段见 §2）
Response: JSON { respCode, respMsg, traceNo, tradeNo, payType, ... }
成功: respCode = "00"
```

| 项 | 规则 |
| --- | --- |
| 金额 | **分**（整数）；HIS 元 → ×100 |
| traceNo | 请求生成；**响应必须一致**，否则拒收 |
| merTradeNo | 商户唯一；Generator 规则见 §3 |
| 超时 | connect 30s / read 60s（可配置） |

---

## 2. 公共字段

### 2.1 请求公共字段

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| mid | String | ✅ | 商户号（`contract.*.mid`） |
| tid | String | ✅ | 终端号（`contract.*.tid`） |
| traceNo | String | ✅ | 流水号；每次请求唯一 |
| tranType | String | ✅ | 交易类型，见 §4 |
| txnAmt | String/Long | ✅* | 金额（分）；查询/撤销类可空 |
| merTradeNo | String | ✅* | 商户订单号 |
| posNo | String | ✅ | 终端编号（Practitioner 或默认） |
| scanCode | String | C 必填 | 付款码 |
| vfTradeNo | String | R 必填 | 原 BPC tradeNo |
| orgId | String | 可选 | 院区路由（Whale 扩展） |

### 2.2 响应公共字段

| 字段 | 说明 |
| --- | --- |
| respCode | `00` 成功；其它见 §5 |
| respMsg | 可读描述 |
| traceNo | 必须与请求一致 |
| tradeNo | BPC 交易号（成功后有值） |
| payType | UPAY / WEIX / ZFBA / 0001 / DCEP |
| txnAmt | 实际金额（分） |
| merTradeNo | 回显 |

---

## 3. merTradeNo 规则（C 层）

| ruleId | 格式 | 旧系统 | 默认 |
| --- | --- | --- | --- |
| `PAYMENT_INDEX_TS` | `{paymentId}IND{detailIndex}T{yyyyMMddHHmmss}` | 农大/长大等 | **P0 默认** |
| `PAYMENT_UUID` | `{paymentId}_{uuid8}` | 部分院点 | 可选 |
| `PROJECT_CUSTOM` | project-H* Bean | 红旗等 | L2 覆盖 |

**幂等**：同一 merTradeNo 重复 C 交易，BPC 应返回已存在结果或明确错误；Whale 本地 `upc_bpc_order` UK 防双扣。

---

## 4. tranType 对照

| tranType | 名称 | 典型请求额外字段 | P0/P1/P2 | bpc-ums-cn-v1 |
| --- | --- | --- | --- | --- |
| **C** | 被扫消费 | scanCode, txnAmt | **P0** | ✅ |
| **G** | 支付结果查询 | merTradeNo 或 tradeNo | **P0** | ✅ |
| **F** | 主扫下单 | txnAmt；返回 payData | P1 | ✅ |
| **R** | 退货 | vfTradeNo, txnAmt（部分退） | P1 | ✅ |
| **J** | 退款结果查询 | merTradeNo | P1 | ✅ |
| **D** | 消费撤销 | vfTradeNo | P2 | ⚠️ 部分收单行不支持 |
| **S** | 关闭订单 | merTradeNo | P2 | ⚠️ 部分收单行不支持 |
| **Z** | 撤销结果查询 | — | P2 | 视收单行 |
| **N** | 主扫异步通知 | 回调 Body | P1 | ✅（验签见 Profile） |

> UI 与 AppService **必须**经 `BpcAcquirerProfile.supportsTranType()` 门控；不支持则隐藏按钮。

---

## 5. respCode 映射（bpc-ums-cn-v1 基线）

| respCode | 含义 | Whale 处理 |
| --- | --- | --- |
| **00** | 成功 | success=true；写 SUCCESS |
| 03 | 无效商户 | BusinessException；检查 mid/tid |
| 04 | 无效金额 | 金额校验失败 |
| 05 | 无效付款码 | 提示患者换码 |
| 12 | 重复交易 | 查单 G 补状态 |
| 51 | 余额不足 | 展示 respMsg |
| 54 | 过期卡/码 | 提示重扫 |
| 55 | 密码错 | — |
| 58 | 终端无效 | 检查 posNo/tid |
| 96 | 系统异常 | 可重试 + 查单 |
| 98 | 超时 | 进入 POLLING |
| 其它 | — | `BusinessException(respMsg)` |

Profile 可扩展 `respCode → userMessage` 映射表；默认上表。

---

## 6. payType 双存（BPC 原值 ↔ HIS pay_enum）

> **定稿 2026-06-23**（[01-概要设计](./01-概要设计.md)）：BPC 接口记原值；HIS 渠道分类用 **语义码** `pay_enum`；二者 **不可混写**。  
> **范围**：本节 **仅银联被扫**。同一 `pay_enum` 也可能由 health 手工渠道写入（无 `upc_bpc_order`），见 **ADR-PAY-001 §4.4**。

| BPC `pay_type`（`upc_bpc_order`） | 含义 | HIS `pay_enum`（`fin_payment_rec_detail`） | `PaymentChannel` |
| --- | --- | --- | --- |
| WEIX | 微信（被扫） | **WECHAT** | `WECHAT` |
| ZFBA | 支付宝（被扫） | **ALIPAY** | `ALIPAY` |
| UPAY | 云闪付/银联扫码 | **BANK_CARD** | `BANK_CARD` |

实现：`BpcPayTypeMapper.toPayEnum(bpcPayType)`（`health-domain-shared`）写 PaymentRecDetail；`upc_bpc_order.pay_type` 存 BPC 响应原值。

> **勿用** YbPayment `220100`～`220400` 作为 Whale 收银落库值；老 JSP 对照见院点文档 §5.5「只读」说明。

---

## 7. 收单行 Profile 矩阵

| profileId | 说明 | 阶段 | supportsCloseOrder | supportsReverse | 备注 |
| --- | --- | --- | --- | --- | --- |
| **bpc-ums-cn-v1** | 银联商务 BPC 基线 | P0 | false | false | 迁移 BankPosCloud |
| bpc-ums-jl-xx | 吉林省行定制 URL | P1 | 院点登记 | 院点登记 | properties `request_url` |
| bpc-ums-hq-v1 | 红旗 fieldMapping posjh→posNo | P1 | false | false | Enricher |

**新增 Profile 流程**：

1. 在本表登记 profileId 与能力位  
2. 实现 `BpcAcquirerProfile` 子类或配置 seed  
3. [06-P0手工测试清单](./06-P0手工测试清单.md) 增补院点用例  
4. project-H* 如需自定义 Generator，仅 L2 覆盖

---

## 8. 签名协议（A 层）

| signVersion | 算法 | 阶段 |
| --- | --- | --- |
| **md5-body-ampersand-secret-v1** | `MD5(jsonBody + "&" + secret)` 小写 hex | **P0 默认** |

Header 名：`sign`。body 为 **紧凑 JSON 字符串**（与旧 BpcHttpUtil 一致）。

---

## 9. 主扫通知 N（P1）

| 项 | 说明 |
| --- | --- |
| URL | 院点公网 `POST /api/app/unionPayBpc/scanNotify`（或反向代理） |
| 验签 | Profile `verifyNotifySign(body, sign)` |
| 幂等 | merTradeNo / tradeNo UK |
| 响应 | `{ "respCode": "00" }` |

---

## 10. 与 boc-pay 差异（同族不同 Module）

| 维度 | unionpay-bpc（本文） | boc-pay（中行 Comlink） |
| --- | --- | --- |
| 接入 | 银联商务 BPC / OpenHIS.Win DLL | 中行 Comlink 云平台 |
| URL | 单 `contract.*.request_url` | 正扫/反扫 **双 URL** |
| 代码 | `zxplat-module-unionpay-bpc` | `zxplat-module-boc-pay` |
| 文档 | 本文 | [zxplat-module-boc-pay/00](../zxplat-module-boc-pay/00-功能清单.md) |

**禁止**同院同时装配两 Module。

---

## 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.3 | 2026-06-23 | 对齐 ADR-PAY-001：WEIX→WECHAT、ZFBA→ALIPAY、UPAY→BANK_CARD |
| v1.2 | 2026-06-23 | payType 双存映射 220100/220200/220300 |
| v1.1 | 2026-06-23 | mid/tid 来源改 contract.properties |
| v1.0 | 2026-06-22 | 首版：JSON 报文、tranType、respCode、Profile 基线 |
