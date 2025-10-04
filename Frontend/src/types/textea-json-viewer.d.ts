declare module '@textea/json-viewer' {
  import type { ComponentType } from 'react';

  export interface JsonViewerProps {
    value: unknown;
    defaultInspectDepth?: number;
    rootName?: string | false;
    editable?: boolean;
    displayDataTypes?: boolean;
    collapseStringsAfterLength?: number;
    enableClipboard?: boolean;
    theme?: string;
  }

  export const JsonViewer: ComponentType<JsonViewerProps>;
}
