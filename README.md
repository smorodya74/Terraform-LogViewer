# <img width="64" height="64" alt="Logo" src="https://github.com/user-attachments/assets/a873c675-12cc-4544-bb98-2437a63cb335" /> Terraform-LogViewer 
Анализатор логов с генерацией диаграммы Ганта

### ⚙️ Технологический стек

- Docker (docker-compose)

#### 🧱 Backend
- Java 21
- Spring Boot 3.5.6
- PostgreSQL 16
- Ingest

#### 🌠 Frontend 
- React 18 (TypeScript)
- Ant Design 5

--- 

### 🧩 Архитектура проекта

<img width="640" height="480" alt="3452342" src="https://github.com/user-attachments/assets/c8d70934-db4f-4581-b38c-8671b677a931" />

--- 

### 🛠 Установка и запуск

```bash
docker compose up --build
```
Перейдите по адресу:
```
http://localhost:5173
```

Сервисы:
- `backend` — gRPC (9090), REST (8081 внутри контейнера).
- `envoy` — grpc-web и REST прокси `http://localhost:8080`.
- `frontend` — Vite dev (`http://localhost:5173`).
- `pg` — Postgres (порт 5433 наружу).

--- 

### 🔗 Архитектура вызовов
| UI | RPC |
| --- | --- |
| `/import` UploadDragger | `LogIngest.IngestFile` (grpc-web) или `/api/imports/upload` (REST/gateway) |
| `/analyze` таблица | `LogQuery.Search`, `LogQuery.MarkRead`, `LogQuery.GetLog`, `LogQuery.Bodies` |
| `/analyze` тепловая карта | `LogQuery.Export` (stream) → агрегация по bucket |
| `/timeline` диаграмма | `LogQuery.Timeline` (stream) |

<img width="1280" height="720" alt="image_2025-10-02_15-27-30" src="https://github.com/user-attachments/assets/6f553750-0ca7-4ae6-b918-0159b980df87" />
Источник: https://app.ilograph.com/@seeseesesese/agage

--- 

### 🧙‍♂️ Team
`Product Manager`, `UI/UX Designer`, `QA Engineer`, `Repository Holder`: Смородников Степан <br>
`Frontend Developer`: Стуров Дмитрий <br>
`Backend Developer`: Стуров Юрий  <br>
`Devops`: Собакарь Владимир <br>
