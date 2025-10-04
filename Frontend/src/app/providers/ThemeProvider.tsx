import { ConfigProvider, theme } from 'antd';
import { PropsWithChildren } from 'react';

const ThemeProvider = ({ children }: PropsWithChildren) => (
  <ConfigProvider
    theme={{
      algorithm: theme.defaultAlgorithm,
      token: {
        colorPrimary: '#1677ff',
      },
    }}
  >
    {children}
  </ConfigProvider>
);

export default ThemeProvider;
