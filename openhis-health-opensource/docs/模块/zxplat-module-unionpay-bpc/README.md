# Whale UnionPay-BPC 文档

银联商务扫码收单扩展 Module（v3）。

> **文档目录**：`docs/模块/zxplat-module-zxplat-module-unionpay-bpc/`（与代码工程同名）  
> **架构真源**：[17-zxplat-module-unionpay-bpc-架构-v3.md](./17-zxplat-module-unionpay-bpc-架构-v3.md) · 代码 `zxplat-module-unionpay-bpc`  
> **P0 出站**：OpenHIS.Win 本地桥（DLL）+ 本 Module HIS 落账

## 阅读顺序

| 序号 | 文档 | 说明 |
| --- | --- | --- |
| **17** | [17-zxplat-module-unionpay-bpc-架构-v3.md](./17-zxplat-module-unionpay-bpc-架构-v3.md) | **架构 v3** |
| **13** | [13-实现状态与文档对照.md](./13-实现状态与文档对照.md) | 实现对照 |
| **00～03** | 功能 / HLD / 交互 / 成码 | 业务 LLD |
| **08** | [08-OpenHIS.Win银联本地桥接.md](./08-OpenHIS.Win银联本地桥接.md) | P0 Win 桥 |
| — | [模块说明-代码结构.md](./模块说明-代码结构.md) | Maven + Java 包 |

## 代码

```text
zxplat-plugin/zxplat-module-zxplat-module-unionpay-bpc/ ```

Profile：`-Pwith-unionpay-bpc` · `zxplat.modules.unionpay-bpc.enabled=true`

## 成码

- Rule：`docs/模块/.cursor/rules/whale-unionpay-bpc-codegen.mdc`
