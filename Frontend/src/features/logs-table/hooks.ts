import { useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import logApi from '../../entities/logs/api';
import type { GroupedLogsResponse, LogsResponse } from '../../entities/logs/api';
import { LogsFiltersState, useFilters } from '../../shared/store/ui';

export interface LogsQueryParams extends LogsFiltersState {
  page: number;
  pageSize: number;
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
}

export type LogsQueryResult =
  | { kind: 'flat'; payload: LogsResponse }
  | { kind: 'group'; payload: GroupedLogsResponse };

export const useLogsQuery = (
  page: number,
  pageSize: number,
  sortBy: string | undefined,
  sortDir: 'asc' | 'desc' | undefined,
  groupByReqId: boolean,
) => {
  const filters = useFilters();
  const [searchParams] = useSearchParams();

  const merged = useMemo(() => {
    const importId = searchParams.get('importId') ?? undefined;
    return {
      ...filters,
      import_id: importId,
    } as LogsFiltersState & { import_id?: string };
  }, [filters, searchParams]);

  return useQuery<LogsQueryResult>({
    queryKey: ['logs', { page, pageSize, sortBy, sortDir, groupByReqId, ...merged }],
    queryFn: async () => {
      if (groupByReqId) {
        const payload = await logApi.searchLogGroups({
          page,
          pageSize,
          sortBy,
          sortDir,
          groupByReqId: true,
          ...merged,
        });
        return { kind: 'group', payload } satisfies LogsQueryResult;
      }
      const payload = await logApi.searchLogs({
        page,
        pageSize,
        sortBy,
        sortDir,
        groupByReqId: false,
        ...merged,
      });
      return { kind: 'flat', payload } satisfies LogsQueryResult;
    },
    placeholderData: (previousData) => previousData,
  });
};
