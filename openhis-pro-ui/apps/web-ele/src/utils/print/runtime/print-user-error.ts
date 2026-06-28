import { h } from 'vue';

import { ElMessage, ElMessageBox } from 'element-plus';

import PrintTroubleshootingDialogBody from '../components/print-troubleshooting-dialog-body.vue';

import { resolvePrintErrorMessage } from './errors';
import { PRINT_ERROR_CODE } from './types';

/** Chrome 本地网络权限 flags 地址（须用户粘贴到地址栏，网页无法直接跳转） */
export const PRINT_CHROME_FLAG_URL = 'chrome://flags/#local-network-access-check';

/** 第一步排查条目 */
export const PRINT_PLUGIN_INSTALL_BULLETS = [
  '系统托盘是否出现 OpenHIS.Win 图标',
  '程序启动后会自动拉起打印服务',
  '若未安装，请联系信息科获取安装包并完成安装',
];

/** 复制 Chrome flags 地址到剪贴板 */
export async function copyPrintChromeFlagUrl(): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(PRINT_CHROME_FLAG_URL);
    ElMessage.success('已复制 Chrome 设置地址，请粘贴到地址栏后回车');
    return true;
  } catch {
    ElMessage.warning(`请手动复制：${PRINT_CHROME_FLAG_URL}`);
    return false;
  }
}

/** 第一步：确认本机 OpenHIS.Win 打印插件（纯文本，供 Alert 等场景） */
export const PRINT_PLUGIN_INSTALL_MESSAGE = [
  '第一步：确认本机打印插件',
  '',
  '浏览器无法自动判断插件是否已安装，请自行确认：',
  ...PRINT_PLUGIN_INSTALL_BULLETS.map((item) => `· ${item}；`),
].join('\n');

/** 第二步说明（纯文本） */
export const PRINT_CHROME_FLAG_INTRO = [
  '第二步：调整 Chrome 本地网络权限',
  '',
  '若插件已安装并启动，仍无法获取打印机或打印失败：',
  '将 Local network access checks 设为 Disabled，然后重启 Chrome 后重试。',
].join('\n');

export const PRINT_CHROME_FLAG_MESSAGE = [
  PRINT_CHROME_FLAG_INTRO,
  '',
  `设置地址：${PRINT_CHROME_FLAG_URL}`,
].join('\n');

export const PRINT_TROUBLESHOOTING_MESSAGE = [
  '请点击右上角头像，打开「打印设置」按以下步骤排查：',
  '',
  PRINT_PLUGIN_INSTALL_MESSAGE,
  '',
  PRINT_CHROME_FLAG_MESSAGE,
].join('\n');

const CONNECTIVITY_ERROR_CODES = new Set<string>([
  PRINT_ERROR_CODE.CLIENT_OFFLINE,
  PRINT_ERROR_CODE.PRINTER_NOT_FOUND,
  PRINT_ERROR_CODE.PRINT_FAILED,
]);

const CONNECTIVITY_MESSAGE_PATTERN =
  /无法连接|未检测到|Hiprint|打印客户端|打印机|printer|local network|网络访问/i;

/** 是否应向用户展示打印排查指引 */
export function shouldShowPrintTroubleshooting(code: string, message?: string): boolean {
  if (code === PRINT_ERROR_CODE.PRINT_BUSY) {
    return false;
  }
  if (CONNECTIVITY_ERROR_CODES.has(code)) {
    return true;
  }
  const text = message?.trim();
  return Boolean(text && CONNECTIVITY_MESSAGE_PATTERN.test(text));
}

/** 面向用户的打印失败提示（排查类用排版弹窗，其余用 ElMessage） */
export function notifyPrintError(options: {
  code: string;
  message?: string;
  title?: string;
}): void {
  const code = options.code.trim();
  const detail = options.message?.trim();
  const title = options.title ?? '打印失败';

  if (!shouldShowPrintTroubleshooting(code, detail)) {
    ElMessage.error(detail || resolvePrintErrorMessage(code));
    return;
  }

  const summary =
    detail && detail !== '打印失败' && !detail.includes('chrome://flags')
      ? detail
      : resolvePrintErrorMessage(code);

  void ElMessageBox({
    title,
    message: h(PrintTroubleshootingDialogBody, { summary }),
    showCancelButton: true,
    confirmButtonText: '我知道了',
    cancelButtonText: '复制 Chrome 设置地址',
    distinguishCancelAndClose: true,
    closeOnClickModal: false,
    closeOnPressEscape: false,
    customClass: 'whale-print-troubleshoot-messagebox',
  }).catch((action) => {
    if (action === 'cancel') {
      void copyPrintChromeFlagUrl();
    }
  });
}
