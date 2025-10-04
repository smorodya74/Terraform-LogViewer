import { Flex, Space } from 'antd';
import { PropsWithChildren } from 'react';

const Toolbar = ({ children }: PropsWithChildren) => (
  <Flex
    align="center"
    justify="space-between"
    style={{ marginBottom: 16, gap: 12, flexWrap: 'wrap' }}
  >
    <Space wrap>{children}</Space>
  </Flex>
);

export default Toolbar;
