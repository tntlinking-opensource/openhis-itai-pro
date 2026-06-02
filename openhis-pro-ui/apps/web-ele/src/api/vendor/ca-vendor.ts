/**
 * CA厂商签名适配层 — BJCA xtxasyn.js 实现
 *
 * 通过 BJCA 提供的 xtxasyn.js WebSocket 客户端组件，与本地证书服务通信。
 * xtxasyn.js 通过 WebSocket 连接到 127.0.0.1:21051（HTTP）/ 127.0.0.1:21061（HTTPS），
 * 提供异步回调风格的 API（SOF_GetUserList、SOF_ExportUserCert、SOF_SignData 等）。
 *
 * 重要：xtxasyn.js 初始化时序说明
 * - xtxasyn.js 在 IIFE 中调用 initXTXAppObject → initXTXAppWebSocket → loadWebSocketCtl
 * - loadWebSocketCtl 创建 new WebSocket(url)，这是异步的，不会立即抛出异常
 * - 因此脚本加载完成后 XTXAppWebSocket 可能非 null，但 WebSocket 处于 CONNECTING 状态
 * - 连接成功后 onopen 触发；连接失败后 onclose 触发，xtxasyn 会自动重连
 * - 正确的检测方式：通过 SOF_GetVersion 主动探测，而非仅检查 XTXAppWebSocket 是否为 null
 */

export namespace CaVendorApi {
  /** 签名结果 */
  export interface SignResult {
    /** Base64编码的签名值 */
    signature: string;
    /** 签名算法（可选，服务端从证书自动检测） */
    algorithm?: string;
  }

  /** 证书信息 */
  export interface CertResult {
    /** Base64编码的数字证书（DER格式） */
    certificate: string;
    /** 证书签名算法（可选，服务端自动检测） */
    algorithm?: string;
  }

  /** 用户列表项 */
  export interface UserCertItem {
    /** 证书显示名称（CN） */
    certName: string;
    /** 证书容器ID（用于签名/导出等操作） */
    certID: string;
  }

  /** 客户端检测结果 */
  export interface ClientStatus {
    /** 组件是否可用 */
    available: boolean;
    /** 客户端版本号（可用时返回） */
    version?: string;
    /** 不可用原因 */
    reason?: string;
  }

  /** UKey设备信息（用于新增用户时读取） */
  export interface UKeyDeviceInfo {
    /** 设备序列号 → ukey_id */
    deviceSN: string;
    /** 证书容器ID */
    certID: string;
    /** 证书显示名称(CN) → 填充userName */
    certName: string;
    /** 证书序列号 → ukey_src_id */
    certSerialNumber: string;
  }
}

// ============== xtxasyn 全局对象类型声明 ==============

interface XtxasynRetObj {
  retVal: any;
  ctx: any;
}

interface XtxasynHashMap {
  put(key: string, value: any): void;
  get(key: string): any;
  remove(key: string): boolean;
}

interface XtxasynWsObject {
  socket: WebSocket | undefined;
  wsMessageQueue: XtxasynHashMap;
  wsMessageQueueId: number;
  wsEventQueue: XtxasynHashMap;
  wsURL: string;
  wsCacheMessage: string[];
}

interface XtxasynGlobal {
  XTXAppWebSocket: XtxasynWsObject | null | undefined;
  wsObject: XtxasynWsObject | null | undefined;
  HashMap: new () => XtxasynHashMap;
  util: {
    checkBrowserISIE(): boolean;
    checkLocationIsHttps(): boolean;
    consolelog(param: any): void;
    isEmpty(param: any): boolean;
    loadWebSocketCtl(wsUrl: string, wssUrl: string): XtxasynWsObject | null;
    callWebSocketMethod(
      wsObject: XtxasynWsObject,
      clsid: string,
      method: string,
      cb: (retObj: XtxasynRetObj) => void,
      ctx: any,
      returnType: string,
      argsArray: any[],
    ): void;
    SynToAsyn(retVal: any, cb: (retObj: XtxasynRetObj) => void, ctx: any): void;
  };
  custom: {
    loginToken: string;
    getToken(): string;
    setToken(token: string): void;
    errorReportFunc: (msg: string) => void;
  };
  xtx_clsid: string;
  SOF_GetUserList: (cb: (retObj: XtxasynRetObj) => void, ctx?: any) => void;
  SOF_ExportUserCert: (CertID: string, cb: (retObj: XtxasynRetObj) => void, ctx?: any) => void;
  SOF_SignData: (
    CertID: string,
    InData: string,
    cb: (retObj: XtxasynRetObj) => void,
    ctx?: any,
  ) => void;
  SOF_SignMessage: (
    dwFlag: number,
    CertID: string,
    InData: string,
    cb: (retObj: XtxasynRetObj) => void,
    ctx?: any,
  ) => void;
  SOF_VerifySignedData: (
    Cert: string,
    InData: string,
    SignValue: string,
    cb: (retObj: XtxasynRetObj) => void,
    ctx?: any,
  ) => void;
  SOF_GetCertInfo: (
    Cert: string,
    type: number,
    cb: (retObj: XtxasynRetObj) => void,
    ctx?: any,
  ) => void;
  SOF_ValidateCert: (Cert: string, cb: (retObj: XtxasynRetObj) => void, ctx?: any) => void;
  SOF_Login: (
    CertID: string,
    PassWd: string,
    cb: (retObj: XtxasynRetObj) => void,
    ctx?: any,
  ) => void;
  SOF_Logout: (CertID: string, cb: (retObj: XtxasynRetObj) => void, ctx?: any) => void;
  SOF_GetVersion: (cb: (retObj: XtxasynRetObj) => void, ctx?: any) => void;
  GetAllDeviceSN: (cb: (retObj: XtxasynRetObj) => void, ctx?: any) => void;
}

declare const xtxasyn: XtxasynGlobal;

// ============== Mock 实现（开发调试用） ==============

const isMockMode = import.meta.env.VITE_CA_VENDOR_MOCK === 'true';

// ============== 脚本按需加载（避免首页强制连本地WS） ==============

let xtxasynScriptLoadingPromise: Promise<void> | null = null;

/**
 * 按需加载 BJCA 提供的 `/xtxasyn.js`。
 *
 * 背景：xtxasyn.js 在加载时会立刻尝试连接本机 WebSocket（21051/21061）。
 * 如果把它写在 index.html 里，会导致“未使用CA功能也报错”的噪音。
 */
async function ensureXtxasynScriptLoaded(): Promise<void> {
  if (isMockMode) return;
  if (typeof xtxasyn !== 'undefined') return;

  if (xtxasynScriptLoadingPromise) return xtxasynScriptLoadingPromise;

  xtxasynScriptLoadingPromise = new Promise<void>((resolve, reject) => {
    // SSR/非浏览器环境保护
    if (typeof document === 'undefined') {
      reject(new Error('当前环境不支持加载CA组件脚本'));
      return;
    }

    // 已存在则直接复用（避免重复插入）
    const existing = document.querySelector<HTMLScriptElement>('script[data-ca-xtxasyn="true"]');
    if (existing) {
      existing.addEventListener('load', () => resolve(), { once: true });
      existing.addEventListener('error', () => reject(new Error('加载xtxasyn.js失败')), {
        once: true,
      });
      return;
    }

    const script = document.createElement('script');
    script.src = '/xtxasyn.js';
    script.async = true;
    script.defer = true;
    script.setAttribute('data-ca-xtxasyn', 'true');
    script.onload = () => resolve();
    script.onerror = () => reject(new Error('加载xtxasyn.js失败，请检查前端静态资源是否可访问'));
    document.head.appendChild(script);
  });

  return xtxasynScriptLoadingPromise;
}

async function mockSignByVendor(data: string): Promise<CaVendorApi.SignResult> {
  console.info('[CaVendor:Mock] 签名数据:', data);
  const signature = btoa(data);
  return { signature, algorithm: 'RSA-SHA256' };
}

async function mockGetCertByVendor(): Promise<CaVendorApi.CertResult> {
  console.info('[CaVendor:Mock] 获取Mock证书');
  const mockCert = btoa('MOCK-CERTIFICATE-FOR-DEV-ONLY');
  return { certificate: mockCert, algorithm: 'RSA-SHA256' };
}

async function mockGetUserList(): Promise<CaVendorApi.UserCertItem[]> {
  console.info('[CaVendor:Mock] 获取Mock用户列表');
  return [{ certName: 'Mock用户', certID: 'MockUser' }];
}

// ============== 客户端状态缓存 ==============

/** 客户端检测结果缓存 */
let cachedStatus: CaVendorApi.ClientStatus | null = null;
/** 正在检测中标志，防止并发检测 */
let detecting = false;

// ============== 核心检测逻辑 ==============

/**
 * 检查 xtxasyn 全局对象是否存在（脚本是否已加载）
 */
function isXtxasynScriptLoaded(): boolean {
  return typeof xtxasyn !== 'undefined';
}

/**
 * 检查 WebSocket 连接的 readyState
 *
 * xtxasyn.js 的关键时序问题：
 * - new WebSocket(url) 不抛异常，即使目标不可达
 * - 因此 XTXAppWebSocket 在脚本加载后总是非 null
 * - 但 socket.readyState 可能是 CONNECTING(0) 或 CLOSED(3)
 * - 只有 OPEN(1) 状态才能正常通信
 */
function getWebSocketReadyState(): number | null {
  if (!isXtxasynScriptLoaded()) return null;

  // xtxasyn.wsObject 是当前活跃的 WebSocket 对象
  // 在 onclose 中会被设为 undefined 并重连
  const wsObj = xtxasyn.wsObject || xtxasyn.XTXAppWebSocket;
  if (!wsObj || !wsObj.socket) return null;

  return wsObj.socket.readyState;
}

/**
 * WebSocket readyState 常量
 */
const WS_CONNECTING = 0;
const WS_OPEN = 1;
const WS_CLOSING = 2;
const WS_CLOSED = 3;

/**
 * 将 xtxasyn 回调风格转为 Promise（带超时）
 *
 * xtxasyn 在 WebSocket 未连接时不会回调，可能导致 Promise 永远 pending。
 * 添加超时机制防止无限等待。
 */
function xtxasynToPromise(
  fn: (cb: (retObj: XtxasynRetObj) => void, ctx?: any) => void,
  timeoutMs = 60000,
): Promise<XtxasynRetObj> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      reject(new Error('CA组件响应超时，请确认证书服务正在运行'));
    }, timeoutMs);

    try {
      fn((retObj: XtxasynRetObj) => {
        clearTimeout(timer);
        resolve(retObj);
      });
    } catch (e) {
      clearTimeout(timer);
      reject(e);
    }
  });
}

/**
 * 通过主动调用 SOF_GetVersion 检测客户端是否真正可用
 *
 * 这是最可靠的检测方式：只有 WebSocket 连接建立成功且服务端响应时，
 * SOF_GetVersion 回调才会被触发。
 *
 * @param timeoutMs 超时时间（毫秒）
 * @returns 客户端状态
 */
export async function detectClientStatus(timeoutMs = 5000): Promise<CaVendorApi.ClientStatus> {
  if (isMockMode) {
    return { available: true, version: 'Mock-1.0.0' };
  }

  // 0. 确保脚本已加载（按需）
  try {
    await ensureXtxasynScriptLoaded();
  } catch (e: any) {
    return { available: false, reason: e?.message || 'xtxasyn.js 加载失败' };
  }

  // 1. 检查脚本是否加载
  if (!isXtxasynScriptLoaded()) {
    return {
      available: false,
      reason: 'xtxasyn.js 未加载，请刷新页面或检查网络',
    };
  }

  // 2. 检查 WebSocket 对象是否存在
  //    注意：initXTXAppWebSocket 失败时 XTXAppWebSocket 为 null
  //    且 SOF_GetVersion 等函数不会被注册到 xtxasyn 上
  if (!xtxasyn.XTXAppWebSocket) {
    return {
      available: false,
      reason: 'BJCA客户端未安装或服务未启动，请安装证书应用环境',
    };
  }

  // 3. 检查 SOF_GetVersion 方法是否已注册
  //    initXTXAppObject 中 initXTXAppWebSocket 失败时会 return false
  //    不会执行后续的函数注册逻辑
  if (typeof xtxasyn.SOF_GetVersion !== 'function') {
    return {
      available: false,
      reason: 'BJCA客户端初始化未完成，请刷新页面重试',
    };
  }

  // 4. 检查 WebSocket readyState
  const readyState = getWebSocketReadyState();
  if (readyState === WS_CLOSED || readyState === WS_CLOSING) {
    // WebSocket 关闭中或已关闭，xtxasyn 会自动重连
    // 等待一段时间后重试
    console.info('[CaVendor] WebSocket 状态异常(readyState=' + readyState + ')，等待重连...');
    await new Promise((resolve) => setTimeout(resolve, 2000));
  }

  // 5. 主动调用 SOF_GetVersion 验证连接可用性
  try {
    const retObj = await xtxasynToPromise((cb) => xtxasyn.SOF_GetVersion(cb), timeoutMs);
    const version = retObj.retVal || '';
    if (version) {
      return { available: true, version };
    }
    // retVal 为空也可能是客户端异常
    return {
      available: false,
      reason: 'BJCA客户端响应异常，请重启证书服务',
    };
  } catch (e: any) {
    const msg = e?.message || '';
    if (msg.includes('超时')) {
      return {
        available: false,
        reason: 'CA证书服务连接超时，请确认已安装BJCA客户端且服务正在运行',
      };
    }
    return {
      available: false,
      reason: `CA组件检测失败: ${msg}`,
    };
  }
}

/**
 * 快速检查客户端是否可用（同步方法，使用缓存结果）
 *
 * 注意：首次调用前应先调用 detectClientStatus() 进行异步检测。
 * 此方法仅用于 UI 状态显示，不应作为功能调用的前置条件。
 */
export function isXtxasynReady(): boolean {
  if (isMockMode) return true;
  if (cachedStatus) return cachedStatus.available;

  // 降级：没有缓存时做基本检查
  if (!isXtxasynScriptLoaded()) return false;
  if (!xtxasyn.XTXAppWebSocket) return false;
  if (typeof xtxasyn.SOF_GetVersion !== 'function') return false;

  // 检查 WebSocket 是否处于可工作状态
  const readyState = getWebSocketReadyState();
  // CONNECTING 也算可用（xtxasyn 内部会缓存消息等连接建立后发送）
  return readyState === WS_OPEN || readyState === WS_CONNECTING;
}

/**
 * 异步初始化检测（页面加载时调用）
 *
 * 采用渐进式策略：先等待 WebSocket 连接建立，再主动探测。
 * 如果检测失败，缓存结果但仍允许用户刷新重试。
 */
export async function initDetection(): Promise<CaVendorApi.ClientStatus> {
  if (detecting) {
    // 防止并发检测
    return cachedStatus || { available: false, reason: '正在检测中...' };
  }

  detecting = true;
  try {
    if (isMockMode) {
      cachedStatus = { available: true, version: 'Mock-1.0.0' };
      return cachedStatus;
    }

    // 确保脚本已加载（按需）
    await ensureXtxasynScriptLoaded();

    // 等待 xtxasyn.js 完成初始化（WebSocket 创建和函数注册）
    // xtxasyn.js IIFE 执行时同步创建 WebSocket 并注册函数
    // 但 WebSocket 连接是异步的，需要等待 onopen
    await new Promise((resolve) => setTimeout(resolve, 1500));

    // 如果脚本未加载完成
    if (!isXtxasynScriptLoaded()) {
      cachedStatus = {
        available: false,
        reason: 'xtxasyn.js 未加载，请刷新页面',
      };
      return cachedStatus;
    }

    // 通过主动调用来验证
    cachedStatus = await detectClientStatus(5000);
    return cachedStatus;
  } finally {
    detecting = false;
  }
}

/**
 * 清除缓存状态，允许重新检测（如用户点击刷新按钮时）
 */
export function resetDetection(): void {
  cachedStatus = null;
}

// ============== 业务 API ==============

/**
 * 获取用户证书列表
 *
 * xtxasyn.SOF_GetUserList 返回格式: "CN1||CertID1&&&CN2||CertID2&&&..."
 *
 * @returns 用户证书列表
 */
export async function getUserList(): Promise<CaVendorApi.UserCertItem[]> {
  if (isMockMode) {
    return mockGetUserList();
  }

  // 直接尝试调用，xtxasyn 内部会处理 WebSocket 状态
  // 如果 WebSocket 未连接，消息会被缓存到 wsCacheMessage 等连接后发送
  try {
    const retObj = await xtxasynToPromise((cb) => xtxasyn.SOF_GetUserList(cb));
    const strUserList: string = retObj.retVal || '';

    if (!strUserList || strUserList === '') {
      throw new Error('未检测到CA证书，请插入USB Key或确认已安装证书');
    }

    const users: CaVendorApi.UserCertItem[] = [];
    let remaining = strUserList;

    while (remaining) {
      const separatorIndex = remaining.indexOf('&&&');
      let strOneUser: string;

      if (separatorIndex > 0) {
        strOneUser = remaining.substring(0, separatorIndex);
        remaining = remaining.substring(separatorIndex + 3);
      } else {
        strOneUser = remaining;
        remaining = '';
      }

      const nameCertSplit = strOneUser.indexOf('||');
      if (nameCertSplit > 0) {
        const certName = strOneUser.substring(0, nameCertSplit);
        const certID = strOneUser.substring(nameCertSplit + 2);
        users.push({ certName, certID });
      }
    }

    return users;
  } catch (e: any) {
    // 超时或连接错误 → 重新检测客户端状态
    resetDetection();
    throw e;
  }
}

/**
 * 使用厂商组件获取数字证书（Base64编码）
 *
 * @param certID 证书容器ID（从getUserList获取）
 * @returns 证书信息
 */
export async function getCertByVendor(certID?: string): Promise<CaVendorApi.CertResult> {
  if (isMockMode) {
    return mockGetCertByVendor();
  }

  // 如果未指定 certID，自动获取第一个
  let targetCertID = certID;
  if (!targetCertID) {
    const users = await getUserList();
    if (users.length === 0) {
      throw new Error('未检测到CA证书，请插入USB Key或确认已安装证书');
    }
    targetCertID = users[0]!.certID;
  }

  // SOF_ExportUserCert: 导出签名证书（Base64编码）
  const retObj = await xtxasynToPromise((cb) => xtxasyn.SOF_ExportUserCert(targetCertID!, cb));

  const certificate: string = retObj.retVal || '';
  if (!certificate) {
    throw new Error('导出证书失败，请确认证书有效');
  }

  // 算法检测改为可选，服务端从证书公钥自动判断
  // 前端仍做基础检测用于UI展示，但不再作为必须字段
  let algorithm: string | undefined;
  try {
    const algRetObj = await xtxasynToPromise((cb) => xtxasyn.SOF_GetCertInfo(certificate, 2, cb));
    const algStr: string = algRetObj.retVal || '';
    if (algStr.includes('SM2') || algStr.includes('1.2.156.10197')) {
      algorithm = 'SM2-SM3';
    } else if (algStr.includes('RSA') || algStr.includes('1.2.840.113549')) {
      algorithm = 'RSA-SHA256';
    }
  } catch {
    // 获取算法失败时不影响主流程
  }

  return { certificate, algorithm };
}

/**
 * 使用厂商组件对数据进行签名
 *
 * @param data 待签名数据（挑战值nonce）
 * @param certID 证书容器ID
 * @returns 签名结果
 */
export async function signByVendor(data: string, certID?: string): Promise<CaVendorApi.SignResult> {
  if (isMockMode) {
    return mockSignByVendor(data);
  }

  // 如果未指定 certID，自动获取第一个
  let targetCertID = certID;
  if (!targetCertID) {
    const users = await getUserList();
    if (users.length === 0) {
      throw new Error('未检测到CA证书，请插入USB Key或确认已安装证书');
    }
    targetCertID = users[0]!.certID;
  }

  // SOF_SignData: 对原文进行签名，返回Base64编码的签名值
  const retObj = await xtxasynToPromise((cb) => xtxasyn.SOF_SignData(targetCertID!, data, cb));

  const signature: string = retObj.retVal || '';
  if (!signature) {
    throw new Error('签名失败，请确认证书有效且未过期');
  }

  // 算法检测改为可选，服务端自动判断
  let algorithm: string | undefined;
  try {
    const certRetObj = await xtxasynToPromise((cb) =>
      xtxasyn.SOF_ExportUserCert(targetCertID!, cb),
    );
    const cert = certRetObj.retVal || '';
    if (cert) {
      const algRetObj = await xtxasynToPromise((cb) => xtxasyn.SOF_GetCertInfo(cert, 2, cb));
      const algStr: string = algRetObj.retVal || '';
      if (algStr.includes('SM2') || algStr.includes('1.2.156.10197')) {
        algorithm = 'SM2-SM3';
      } else if (algStr.includes('RSA') || algStr.includes('1.2.840.113549')) {
        algorithm = 'RSA-SHA256';
      }
    }
  } catch {
    // 获取算法失败时不影响主流程
  }

  return { signature, algorithm };
}

/**
 * 使用厂商组件对数据进行PKCS7签名（Detached模式）
 *
 * SOF_SignMessage(dwFlag=1, CertID, InData) 返回Detached PKCS7签名，
 * 包含完整的签名算法信息（包括SM2 ID），服务端可用CMSSignedData验证，
 * 不需要猜测SM2 ID。
 *
 * @param data 待签名数据（挑战值nonce）
 * @param certID 证书容器ID
 * @returns 签名结果（PKCS7格式，Base64编码）
 */
export async function signMessageByVendor(
  data: string,
  certID?: string,
): Promise<CaVendorApi.SignResult> {
  if (isMockMode) {
    return mockSignByVendor(data);
  }

  // 如果未指定 certID，自动获取第一个
  let targetCertID = certID;
  if (!targetCertID) {
    const users = await getUserList();
    if (users.length === 0) {
      throw new Error('未检测到CA证书，请插入USB Key或确认已安装证书');
    }
    targetCertID = users[0]!.certID;
  }

  // SOF_SignMessage: dwFlag=1 表示Detached PKCS7（不包含原文）
  const retObj = await xtxasynToPromise((cb) =>
    xtxasyn.SOF_SignMessage(1, targetCertID!, data, cb),
  );

  const signature: string = retObj.retVal || '';
  if (!signature) {
    throw new Error('PKCS7签名失败，请确认证书有效且未过期');
  }

  // 算法检测改为可选
  let algorithm: string | undefined;
  try {
    const certRetObj = await xtxasynToPromise((cb) =>
      xtxasyn.SOF_ExportUserCert(targetCertID!, cb),
    );
    const cert = certRetObj.retVal || '';
    if (cert) {
      const algRetObj = await xtxasynToPromise((cb) => xtxasyn.SOF_GetCertInfo(cert, 2, cb));
      const algStr: string = algRetObj.retVal || '';
      if (algStr.includes('SM2') || algStr.includes('1.2.156.10197')) {
        algorithm = 'SM2-SM3';
      } else if (algStr.includes('RSA') || algStr.includes('1.2.840.113549')) {
        algorithm = 'RSA-SHA256';
      }
    }
  } catch {
    // 获取算法失败时不影响主流程
  }

  return { signature, algorithm };
}

/**
 * 验证证书有效性
 *
 * @param certificate Base64编码的证书
 * @returns 0=有效，其他=无效
 */
export async function validateCert(certificate: string): Promise<number> {
  if (isMockMode) return 0;

  const retObj = await xtxasynToPromise((cb) => xtxasyn.SOF_ValidateCert(certificate, cb));
  return Number(retObj.retVal);
}

/**
 * 登录证书（输入PIN码验证）
 *
 * @param certID 证书容器ID
 * @param pin PIN码
 * @returns 是否登录成功
 */
export async function loginCert(certID: string, pin: string): Promise<boolean> {
  if (isMockMode) return true;

  const retObj = await xtxasynToPromise((cb) => xtxasyn.SOF_Login(certID, pin, cb));
  return !!retObj.retVal;
}

/**
 * 登出证书
 *
 * @param certID 证书容器ID
 */
export async function logoutCert(certID: string): Promise<boolean> {
  if (isMockMode) return true;

  try {
    const retObj = await xtxasynToPromise((cb) => xtxasyn.SOF_Logout(certID, cb));
    return !!retObj.retVal;
  } catch {
    return true;
  }
}

/**
 * 客户端验证签名（SOF_VerifySignedData）
 *
 * 用于调试：签名后立即在客户端验证签名是否有效。
 * 如果客户端验证通过但服务端验证失败，说明是SM2 ID不匹配等算法问题。
 *
 * @param certificate Base64编码的证书（从SOF_ExportUserCert获取）
 * @param originalData 原文（与SOF_SignData的InData相同）
 * @param signature 签名值（从SOF_SignData获取）
 * @returns 验证是否通过
 */
export async function verifySignedData(
  certificate: string,
  originalData: string,
  signature: string,
): Promise<boolean> {
  if (isMockMode) return true;

  const retObj = await xtxasynToPromise((cb) =>
    xtxasyn.SOF_VerifySignedData(certificate, originalData, signature, cb),
  );
  return !!retObj.retVal;
}

// ============== UKey 设备信息读取（新增用户用） ==============

/**
 * 获取所有UKey设备序列号
 *
 * xtxasyn.GetAllDeviceSN 返回设备序列号字符串
 *
 * @returns 设备序列号列表
 */
export async function getAllDeviceSN(): Promise<string[]> {
  if (isMockMode) {
    return ['MOCK-DEVICE-SN-001'];
  }

  const retObj = await xtxasynToPromise((cb) => xtxasyn.GetAllDeviceSN(cb));
  const snStr: string = retObj.retVal || '';

  if (!snStr) {
    throw new Error('未检测到UKey设备，请插入USB Key');
  }

  // 多个设备序列号可能用特定分隔符，通常是 "&&&"
  return snStr.split('&&&').filter((s) => s.trim() !== '');
}

/**
 * 获取证书序列号
 *
 * 通过 SOF_ExportUserCert 导出证书后，用 SOF_GetCertInfo(cert, 2) 获取序列号
 * type=2 对应"证书序列号"（参考 xtxasyn_demo.html certInfoTypeArray）
 *
 * @param certID 证书容器ID
 * @returns 证书序列号
 */
export async function getCertSerialNumber(certID: string): Promise<string> {
  if (isMockMode) {
    return 'MOCK-SERIAL-001';
  }

  // 先导出证书
  const certRetObj = await xtxasynToPromise((cb) => xtxasyn.SOF_ExportUserCert(certID, cb));
  const certificate: string = certRetObj.retVal || '';
  if (!certificate) {
    throw new Error('导出证书失败');
  }

  // SOF_GetCertInfo(cert, 2) 获取证书序列号
  const infoRetObj = await xtxasynToPromise((cb) => xtxasyn.SOF_GetCertInfo(certificate, 2, cb));
  const serialNumber: string = infoRetObj.retVal || '';
  if (!serialNumber) {
    throw new Error('获取证书序列号失败');
  }

  return serialNumber;
}

/**
 * 一站式读取UKey设备信息（用于新增用户）
 *
 * 流程：getUserList → getAllDeviceSN → getCertSerialNumber
 * 取第一个证书的信息自动填充
 *
 * @returns UKey设备信息
 */
export async function readUKeyInfo(): Promise<CaVendorApi.UKeyDeviceInfo> {
  if (isMockMode) {
    return {
      deviceSN: 'MOCK-DEVICE-SN-001',
      certID: 'MockUser',
      certName: 'Mock用户',
      certSerialNumber: 'MOCK-SERIAL-001',
    };
  }

  // 1. 获取用户证书列表
  const users = await getUserList();
  if (users.length === 0) {
    throw new Error('未检测到CA证书，请插入USB Key');
  }
  const firstUser = users[0]!;

  // 2. 获取设备序列号
  const deviceSNs = await getAllDeviceSN();
  const deviceSN = deviceSNs[0] || '';

  // 3. 获取证书序列号
  const certSerialNumber = await getCertSerialNumber(firstUser.certID);

  return {
    deviceSN,
    certID: firstUser.certID,
    certName: firstUser.certName,
    certSerialNumber,
  };
}
