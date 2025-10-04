import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';

export interface LogsFiltersState {
  q?: string;
  level?: string;
  section?: string;
  resource_type?: string;
  status_code?: number;
  ts_from?: string;
  ts_to?: string;
  import_id?: string;
  req_id?: string;
  tf_req_id?: string;
  unread_only?: boolean;
}

export const ALL_COLUMN_KEYS = [
  'ts',
  'level',
  'section',
  'module',
  'message',
  'req_id',
  'resource_type',
  'status_code',
  'trans_id',
  'rpc',
  'data_source_type',
  'http_op_type',
  'file_name',
  'import_id',
] as const;

export type ColumnKey = (typeof ALL_COLUMN_KEYS)[number];

const DEFAULT_COLUMNS: ColumnKey[] = [
  'ts',
  'level',
  'section',
  'module',
  'message',
  'req_id',
  'resource_type',
  'status_code',
];

interface UIState {
  visibleColumns: ColumnKey[];
  setVisibleColumns: (columns: ColumnKey[]) => void;
  filters: LogsFiltersState;
  setFilters: (filters: Partial<LogsFiltersState>) => void;
  resetFilters: () => void;
  selectedLogId?: string;
  setSelectedLogId: (id?: string) => void;
  groupByReqId: boolean;
  setGroupByReqId: (value: boolean) => void;
}

export const useUIStore = create<UIState>()(
  persist(
    (set) => ({
      visibleColumns: DEFAULT_COLUMNS,
      setVisibleColumns: (columns) => set({ visibleColumns: columns }),
      filters: {},
      setFilters: (updates) =>
        set((state) => ({ filters: { ...state.filters, ...updates } })),
      resetFilters: () => set({ filters: {} }),
      selectedLogId: undefined,
      setSelectedLogId: (id) => set({ selectedLogId: id }),
      groupByReqId: false,
      setGroupByReqId: (value) => set({ groupByReqId: value }),
    }),
    {
      name: 'ui-store',
      storage: createJSONStorage(() => sessionStorage),
      partialize: (state) =>
        ({
          visibleColumns: state.visibleColumns,
          filters: state.filters,
          groupByReqId: state.groupByReqId,
        } as Partial<UIState>),
    },
  ),
);

export const useVisibleColumns = () => useUIStore((state) => state.visibleColumns);
export const useFilters = () => useUIStore((state) => state.filters);
export const useGroupByReqId = () =>
  useUIStore((state) => ({
    groupByReqId: state.groupByReqId,
    setGroupByReqId: state.setGroupByReqId,
  }));
