import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import logApi from '../../entities/logs/api';
import { useFilters } from '../../shared/store/ui';

export const useHeatmapQuery = (bucket: 'minute' | 'hour' | 'day') => {
  const filters = useFilters();
  const [searchParams] = useSearchParams();
  const importId = searchParams.get('importId') ?? undefined;
  const merged = { ...filters, import_id: importId ?? filters.import_id };

  return useQuery({
    queryKey: ['heatmap', bucket, merged],
    queryFn: () => logApi.fetchHeatmap(bucket, merged),
  });
};
