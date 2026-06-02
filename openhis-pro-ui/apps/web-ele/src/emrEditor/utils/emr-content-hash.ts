/** 计算 content + printConfig 哈希，与后端 OpenPdfRenderer.computeContentHash 口径对齐 */

export async function computeEmrContentHash(
  contentJson: string,
  printConfigJson: string,
): Promise<string> {
  const raw = contentJson + printConfigJson;
  if (typeof crypto !== 'undefined' && crypto.subtle?.digest) {
    const buf = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(raw));
    return Array.from(new Uint8Array(buf))
      .map((b) => b.toString(16).padStart(2, '0'))
      .join('');
  }
  return `${raw.length}:${raw.slice(0, 64)}`;
}
