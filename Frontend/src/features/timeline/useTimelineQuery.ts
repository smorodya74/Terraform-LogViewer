import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import logApi from '../../entities/logs/api';
import { LogsFiltersState, useFilters } from '../../shared/store/ui';

export const useTimelineQuery = (params?: Partial<LogsFiltersState>) => {
  const filters = useFilters();
  const [searchParams] = useSearchParams();
  const importId = searchParams.get('importId') ?? undefined;
  const merged = { ...filters, ...params, import_id: importId ?? filters.import_id };
  return useQuery({
    queryKey: ['timeline-groups', merged],
    queryFn: () =>
      logApi.searchLogGroups({
        page: 1,
        pageSize: 200,
        sortBy: 'ts',
        sortDir: 'desc',
        groupByReqId: true,
        ...merged,
      }),
  });
};
