# 医保签到 · 应用服务设计（AS）

> **页面边界**：前端/读卡插件完成 **9001 签到**，获取当日 **sign_no**；会话由 gateway 缓存并持久化 `yb_pub_sign`。**在内** 签到请求与 sign_no 返回；**在外** 业务 infno 调用时的 `ensureSigned` 门禁（gateway 自动）。
>
> **编码**：AS-CHS-FND-002 · **版本**：v0.1 · **日期**：2026-07-11  
> **详细设计编号**：AS-CHS-FND-002

---

## 1. 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 9001 签到；返回 sign_no |
| **在外** | MAC/IP 采集（HeaSec 插件）；签退 9002（未单独 AS） |
| **平台 infno** | 9001 |
| **兼容** | `insuranceSession/signIn` 委托本 AS（deprecated 路由） |

## 2. 接口概览

| IF 编号 | 用户动作 | 读/写 | infno |
|---------|----------|-------|-------|
| IF-CHS-FND-002-W-01 | 医保签到 | 写 | 9001 |

## 3. 关联

- [apis/90xx/9001-签到.md](../apis/90xx/9001-签到.md)
- API：`insuranceSign/signIn`
