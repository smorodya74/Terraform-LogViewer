import { useEffect, useMemo, useState } from 'react';
import { Space, Tree, Typography } from 'antd';
import type { DataNode } from 'antd/es/tree';

type JsonValue = unknown;

interface JsonInspectorProps {
  value: JsonValue;
  expandAll?: boolean;
  collapseStringsAfterLength?: number;
}

interface BuildResult {
  nodes: DataNode[];
  allKeys: string[];
  defaultExpanded: string[];
}

const DEFAULT_EXPANDED_DEPTH = 1;

const isRecord = (value: JsonValue): value is Record<string, JsonValue> =>
  Boolean(value) && typeof value === 'object' && !Array.isArray(value);

const formatPrimitive = (value: JsonValue, collapseThreshold?: number) => {
  if (value === null) {
    return <Typography.Text type="secondary">null</Typography.Text>;
  }
  if (value === undefined) {
    return <Typography.Text type="secondary">undefined</Typography.Text>;
  }
  if (typeof value === 'boolean') {
    return <Typography.Text code>{value ? 'true' : 'false'}</Typography.Text>;
  }
  if (typeof value === 'number' || typeof value === 'bigint') {
    return <Typography.Text code>{String(value)}</Typography.Text>;
  }
  if (typeof value === 'string') {
    const shouldCollapse =
      typeof collapseThreshold === 'number' && value.length > collapseThreshold;
    const displayValue = shouldCollapse ? `${value.slice(0, collapseThreshold)}…` : value;
    return (
      <Typography.Text code title={value} style={{ whiteSpace: 'pre-wrap' }}>
        {displayValue}
      </Typography.Text>
    );
  }
  return <Typography.Text>{String(value)}</Typography.Text>;
};

const buildNodes = (
  value: JsonValue,
  parentKey: string,
  depth: number,
  collapseStringsAfterLength?: number,
): BuildResult => {
  if (Array.isArray(value)) {
    const allKeys: string[] = [];
    const defaultExpanded: string[] = [];
    const nodes: DataNode[] = value.map((item, index) => {
      const nodeKey = `${parentKey}[${index}]`;
      const child = buildNodes(item, nodeKey, depth + 1, collapseStringsAfterLength);
      allKeys.push(nodeKey, ...child.allKeys);
      defaultExpanded.push(...child.defaultExpanded);
      const title = (
        <Space size={4}>
          <Typography.Text strong>[{index}]</Typography.Text>
          {Array.isArray(item) || isRecord(item) ? (
            <Typography.Text type="secondary">
              {Array.isArray(item)
                ? `Array(${item.length})`
                : `Object(${Object.keys(item ?? {}).length})`}
            </Typography.Text>
          ) : (
            formatPrimitive(item, collapseStringsAfterLength)
          )}
        </Space>
      );
      const node: DataNode = {
        key: nodeKey,
        title,
        children:
          Array.isArray(item) || isRecord(item) ? child.nodes : undefined,
      };
      if ((Array.isArray(item) || isRecord(item)) && depth < DEFAULT_EXPANDED_DEPTH) {
        defaultExpanded.push(nodeKey);
      }
      return node;
    });
    return { nodes, allKeys, defaultExpanded };
  }

  if (isRecord(value)) {
    const entries = Object.entries(value);
    const allKeys: string[] = [];
    const defaultExpanded: string[] = [];
    const nodes: DataNode[] = entries.map(([key, item]) => {
      const nodeKey = `${parentKey}.${key}`;
      const child = buildNodes(item, nodeKey, depth + 1, collapseStringsAfterLength);
      allKeys.push(nodeKey, ...child.allKeys);
      defaultExpanded.push(...child.defaultExpanded);
      const title = (
        <Space size={4}>
          <Typography.Text strong>{key}</Typography.Text>
          {Array.isArray(item) || isRecord(item) ? (
            <Typography.Text type="secondary">
              {Array.isArray(item)
                ? `Array(${item.length})`
                : `Object(${Object.keys(item ?? {}).length})`}
            </Typography.Text>
          ) : (
            formatPrimitive(item, collapseStringsAfterLength)
          )}
        </Space>
      );
      const node: DataNode = {
        key: nodeKey,
        title,
        children:
          Array.isArray(item) || isRecord(item) ? child.nodes : undefined,
      };
      if ((Array.isArray(item) || isRecord(item)) && depth < DEFAULT_EXPANDED_DEPTH) {
        defaultExpanded.push(nodeKey);
      }
      return node;
    });
    return { nodes, allKeys, defaultExpanded };
  }

  const nodeKey = parentKey || 'root';
  return {
    nodes: [
      {
        key: nodeKey,
        title: formatPrimitive(value, collapseStringsAfterLength),
      },
    ],
    allKeys: [nodeKey],
    defaultExpanded: [],
  };
};

const JsonInspector = ({
  value,
  expandAll = false,
  collapseStringsAfterLength,
}: JsonInspectorProps) => {
  const { treeData, allKeys, defaultExpanded } = useMemo(() => {
    if (Array.isArray(value) || isRecord(value)) {
      const result = buildNodes(value, 'root', 0, collapseStringsAfterLength);
      return {
        treeData: result.nodes,
        allKeys: result.allKeys,
        defaultExpanded: result.defaultExpanded,
      };
    }
    const primitive = formatPrimitive(value, collapseStringsAfterLength);
    return {
      treeData: [
        {
          key: 'root',
          title: primitive,
        },
      ],
      allKeys: ['root'],
      defaultExpanded: [],
    };
  }, [value, collapseStringsAfterLength]);

  const [expandedKeys, setExpandedKeys] = useState<string[]>(
    expandAll ? allKeys : defaultExpanded,
  );

  useEffect(() => {
    setExpandedKeys(expandAll ? allKeys : defaultExpanded);
  }, [expandAll, allKeys, defaultExpanded]);

  if (!treeData.length) {
    return <Typography.Text type="secondary">Пусто</Typography.Text>;
  }

  return (
    <Tree
      blockNode
      showLine
      selectable={false}
      treeData={treeData}
      expandedKeys={expandedKeys}
      onExpand={(keys) => setExpandedKeys(keys as string[])}
    />
  );
};

export default JsonInspector;
