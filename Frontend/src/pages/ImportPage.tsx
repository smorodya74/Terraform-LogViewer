import { Card, Typography } from 'antd';
import UploadDragger from '../features/file-upload/UploadDragger';

const ImportPage = () => (
  <Card title="Импорт логов" bordered={false} style={{ minHeight: '100%' }}>
    <Typography.Paragraph>
      Перетащите JSON-файлы или выберите их для загрузки на сервер. После
      успешной обработки вы сможете перейти к анализу данных.
    </Typography.Paragraph>
    <UploadDragger />
  </Card>
);

export default ImportPage;
