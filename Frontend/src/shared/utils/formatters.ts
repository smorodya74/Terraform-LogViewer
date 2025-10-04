import dayjs from './dayjs';

export const formatTimestamp = (ts?: string | null) =>
  ts ? dayjs(ts).tz().format('YYYY-MM-DD HH:mm:ss.SSS') : '';

export const levelColorMap: Record<string, string> = {
  ERROR: 'red',
  WARN: 'orange',
  INFO: 'blue',
  DEBUG: 'default',
};
