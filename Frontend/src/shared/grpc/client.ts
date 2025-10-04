import { createPromiseClient } from '@bufbuild/connect';
import type { Transport } from '@bufbuild/connect';
import { createGrpcWebTransport } from '@bufbuild/connect-web';
import { LogIngest, LogQuery, ReportService } from '../../gen/logviewer_connectweb.js';
import { resolveApiBaseUrl } from '../config/apiBase';

const baseTransport = createGrpcWebTransport({
  baseUrl: resolveApiBaseUrl(import.meta.env.VITE_GRPC_WEB_BASE),
  useBinaryFormat: true,
});

const ensureGrpcWebHeaders = (input?: HeadersInit): Headers => {
  const headers = new Headers(input);
  headers.set('content-type', 'application/grpc-web+proto');
  headers.set('x-grpc-web', '1');
  headers.set('x-user-agent', '@bufbuild/connect-web');
  headers.delete('connect-protocol-version');
  headers.delete('connect-content-encoding');
  headers.delete('connect-accept-encoding');
  return headers;
};

const transport: Transport = {
  async unary(service, method, signal, timeoutMs, header, message) {
    return baseTransport.unary(
      service,
      method,
      signal,
      timeoutMs,
      ensureGrpcWebHeaders(header),
      message,
    );
  },
  async stream(service, method, signal, timeoutMs, header, input) {
    return baseTransport.stream(
      service,
      method,
      signal,
      timeoutMs,
      ensureGrpcWebHeaders(header),
      input,
    );
  },
};

export const logQueryClient = createPromiseClient(LogQuery, transport);
export const logIngestClient = createPromiseClient(LogIngest, transport);
export const reportClient = createPromiseClient(ReportService, transport);
