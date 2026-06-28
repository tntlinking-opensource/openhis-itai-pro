import { ElMessageBox } from 'element-plus';

function escapeHtml(text: string): string {
  return text
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

/** 需用户手动关闭的明显错误提示（不会自动消失）。 */
export async function showPersistentUserError(options: {
  confirmButtonText?: string;
  message: string;
  title: string;
}): Promise<void> {
  const html = escapeHtml(options.message).replaceAll('\n', '<br/>');
  await ElMessageBox.alert(html, options.title, {
    type: 'error',
    confirmButtonText: options.confirmButtonText ?? '我知道了',
    dangerouslyUseHTMLString: true,
    closeOnClickModal: false,
    closeOnPressEscape: false,
    customClass: 'whale-persistent-error-messagebox',
  });
}
