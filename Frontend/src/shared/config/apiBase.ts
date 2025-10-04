const DEFAULT_PORT = '8080';

const getWindow = () => (typeof window === 'undefined' ? null : window);

const SERVICE_HOSTNAMES = new Set(['envoy', 'backend']);

const normalizeServiceName = (hostname: string) =>
  hostname.split('.')[0]?.replace(/-\d+$/, '') ?? hostname;

const resolveBrowserHostname = (win: Window | null) =>
  win?.location.hostname?.trim() || 'localhost';

const sanitizeHostname = (hostname: string, win: Window | null) => {
  const normalized = normalizeServiceName(hostname.toLowerCase());
  if (SERVICE_HOSTNAMES.has(normalized)) {
    return resolveBrowserHostname(win);
  }

  return hostname;
};

const buildOrigin = (url: URL, portOverride?: string) => {
  if (portOverride) {
    url.port = portOverride;
  }
  url.pathname = '';
  url.search = '';
  url.hash = '';

  return url.toString().replace(/\/$/, '');
};

const buildFallbackOrigin = (win: Window | null, defaultPort: string | undefined) => {
  const url = win ? new URL(win.location.origin) : new URL('http://localhost');
  url.hostname = resolveBrowserHostname(win);
  return buildOrigin(url, defaultPort);
};

export const resolveApiBaseUrl = (
  rawValue: string | undefined,
  options?: { defaultPort?: string }
): string => {
  const { defaultPort = DEFAULT_PORT } = options || {};
  const win = getWindow();

  const fallback = buildFallbackOrigin(win, defaultPort);

  if (!rawValue) {
    return fallback;
  }

  try {
    const url = new URL(rawValue);
    const originalHostname = url.hostname;
    const sanitizedHostname = sanitizeHostname(originalHostname, win);
    url.hostname = sanitizedHostname;
    const shouldOverridePort = sanitizedHostname !== originalHostname && !url.port;
    return buildOrigin(url, shouldOverridePort ? defaultPort : undefined);
  } catch {
    if (!win) {
      return fallback;
    }

    try {
      const url = new URL(rawValue, win.location.origin);
      const originalHostname = url.hostname;
      const sanitizedHostname = sanitizeHostname(originalHostname, win);
      url.hostname = sanitizedHostname;
      const shouldOverridePort = sanitizedHostname !== originalHostname && !url.port;
      return buildOrigin(url, shouldOverridePort ? defaultPort : undefined);
    } catch {
      return fallback;
    }
  }
};
