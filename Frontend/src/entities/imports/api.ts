import { resolveApiBaseUrl } from '../../shared/config/apiBase';

const restBase = resolveApiBaseUrl(import.meta.env.VITE_REST_BASE);

export interface ImportSummary {
  importId: string;
  fileName: string;
  total: number;
  firstTimestamp: string | null;
  lastTimestamp: string | null;
}

export const fetchImportSummaries = async (): Promise<ImportSummary[]> => {
  const response = await fetch(`${restBase}/api/imports`);
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || response.statusText || 'Не удалось получить список импортов');
  }
  const data = (await response.json()) as ImportSummary[];
  return data.map((item) => ({
    ...item,
    fileName: item.fileName || item.importId,
  }));
};
