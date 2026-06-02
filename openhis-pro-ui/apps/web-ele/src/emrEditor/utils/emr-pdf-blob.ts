/** pdfBase64 → Blob / object URL */

export function pdfBase64ToBlob(pdfBase64: string): Blob {
  const raw = pdfBase64.trim();
  const b64 = raw.includes(',') ? (raw.split(',').pop() ?? raw) : raw;
  const binary = atob(b64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return new Blob([bytes], { type: 'application/pdf' });
}

export function pdfBase64ToObjectUrl(pdfBase64: string): string {
  return URL.createObjectURL(pdfBase64ToBlob(pdfBase64));
}

export function revokePdfObjectUrl(url: string | undefined) {
  if (url?.startsWith('blob:')) {
    URL.revokeObjectURL(url);
  }
}
