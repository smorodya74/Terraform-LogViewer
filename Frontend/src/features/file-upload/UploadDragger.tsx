import { InboxOutlined } from '@ant-design/icons';
import type { UploadProps } from 'antd';
import { Upload, Typography, App } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useState } from 'react';
import { uploadFile } from '../../shared/api/upload';

const { Dragger } = Upload;

const UploadDragger = () => {
  const navigate = useNavigate();
  const { message } = App.useApp();
  const [uploading, setUploading] = useState(false);

  const props: UploadProps = {
    name: 'file',
    multiple: true,
    accept: '.json,application/json',
    disabled: uploading,
    customRequest: async ({ file, onSuccess, onError }) => {
      if (!(file instanceof File)) {
        onError?.(new Error('Некорректный файл'));
        return;
      }
      try {
        setUploading(true);
        const response = await uploadFile(file);
        onSuccess?.(response, file);
        message.success('Файл загружен. Переходим к анализу.');
        navigate(`/analyze?importId=${response.importId}`);
      } catch (error) {
        const err = error as Error;
        message.error(err.message || 'Ошибка загрузки');
        onError?.(err);
      } finally {
        setUploading(false);
      }
    },
    showUploadList: false,
  };

  return (
    <Dragger {...props} style={{ padding: 24 }}>
      <p className="ant-upload-drag-icon">
        <InboxOutlined />
      </p>
      <Typography.Title level={4}>Перетащите JSON-файлы</Typography.Title>
      <Typography.Paragraph>
        Поддерживаются только .json. Файл сразу отправляется на бэкенд без
        сохранения в памяти браузера.
      </Typography.Paragraph>
    </Dragger>
  );
};

export default UploadDragger;
