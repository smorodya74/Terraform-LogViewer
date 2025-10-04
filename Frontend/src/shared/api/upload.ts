import { logIngestClient } from '../grpc/client';
import { resolveApiBaseUrl } from '../config/apiBase';

const ingestMode = (import.meta.env.VITE_INGEST_MODE || 'rest').toLowerCase();
const restBase = resolveApiBaseUrl(import.meta.env.VITE_REST_BASE);

export interface UploadResult {
  importId: string;
  fileName: string;
  total: number;
  saved: number;
  failed: number;
}

const arrayBufferToBase64 = (buffer: ArrayBuffer) => {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  bytes.forEach((b) => {
    binary += String.fromCharCode(b);
  });
  return btoa(binary);
};

export const uploadFile = async (file: File): Promise<UploadResult> => {
  if (ingestMode === 'grpc') {
    const buffer = await file.arrayBuffer();
    const response = await logIngestClient.ingestFile({
      fileName: file.name,
      content: new Uint8Array(buffer),
    });
    return {
      importId: response.importId,
      fileName: response.fileName,
      total: Number(response.total),
      saved: Number(response.saved),
      failed: Number(response.failed),
    };
  }

  const base = restBase;
  if (ingestMode === 'gateway') {
    const buffer = await file.arrayBuffer();
    const payload = {
      fileName: file.name,
      contentBase64: arrayBufferToBase64(buffer),
    };
    const response = await fetch(`${base}/api/imports/upload`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    });
    if (!response.ok) {
      const text = await response.text();
      throw new Error(text || response.statusText);
    }
    return (await response.json()) as UploadResult;
  }

  const formData = new FormData();
  formData.append('file', file);

  const response = await fetch(`${base}/api/imports/upload`, {
    method: 'POST',
    body: formData,
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || response.statusText);
  }

  return (await response.json()) as UploadResult;
};

// grpc-web не поддерживает client-stream (LogIngest.Ingest). Вместо него используем
// unary LogIngest.IngestFile или REST/multipart шлюз, выбранный через VITE_INGEST_MODE.
