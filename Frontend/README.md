# Log Analyzer Frontend

Одностраничное приложение на React + TypeScript + Vite для импорта и анализа логов.

## Быстрый старт

1. Скопируйте `.env.example` в `.env` и пропишите реальный адрес API:
   ```bash
   cp .env.example .env
   ```
   Пример содержимого:
   ```env
   VITE_API_BASE=https://your-backend.example.com
   ```
2. Установите зависимости и запустите dev-сервер:
   ```bash
   npm install
   npm run dev
   ```
3. Приложение будет доступно на [http://localhost:5173](http://localhost:5173).

## Основные возможности

- Импорт JSON-файлов с немедленной отправкой на бэкенд (`POST /api/imports/upload`).
- Интерактивная таблица логов с серверной пагинацией, сортировкой, фильтрацией и ленивой подгрузкой JSON.
- Диаграмма Ганта на базе `vis-timeline` и тепловая карта на `Recharts`.
- Состояние UI (фильтры, видимые колонки) хранится в `Zustand` и синхронизируется с `sessionStorage`.

## Построение production-версии

```bash
npm run build
npm run preview # локальная проверка
```

## Docker

1. Соберите образ:
   ```bash
   docker build -t log-analyzer-frontend .
   ```
2. Запустите контейнер:
   ```bash
   docker run -p 8080:80 --env VITE_API_BASE=https://your-backend.example.com log-analyzer-frontend
   ```

Nginx-конфигурация (`nginx.conf`) настроена на history fallback и содержит заглушку `# TODO: BACKEND_URL` для проксирования `/api` на реальный бэкенд.

## Линтинг

```bash
npm run lint
```

## Переменные окружения

- `VITE_API_BASE` — базовый URL API. Используется во всех сетевых запросах.
