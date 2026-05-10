# Explore With Me

Платформа для публикации событий и управления участием в них. Пользователи могут предлагать мероприятия, находить интересные события и подавать заявки на участие.

---

## Архитектура

Приложение построено на микросервисной архитектуре. Каждый сервис имеет собственную базу данных и общается с остальными только через HTTP.

```
Клиент
  │
  ▼
gateway-server :8080          ← единая точка входа, маршрутизирует запросы
  │
  ├──► user-service            ← пользователи (БД: user-db)
  ├──► event-service           ← события, категории, подборки (БД: ewm-db)
  └──► request-service         ← заявки на участие (БД: request-db)
         │
         └──► stats-server     ← сбор и отдача статистики (БД: stats-db)

Инфраструктура:
  discovery-server :8761       ← Eureka, реестр сервисов
  config-server    :8888       ← централизованная конфигурация
```

Бизнес-сервисы стартуют на **случайных портах** (`server.port: 0`). Адреса регистрируются в Eureka, и gateway/feign-клиенты находят их автоматически через service discovery.

### Все модули

| Модуль | Назначение | БД |
|--------|------------|-----|
| `infra/discovery-server` | Eureka Server — реестр всех сервисов | — |
| `infra/config-server` | Spring Cloud Config — централизованная конфигурация | — |
| `infra/gateway-server` | Spring Cloud Gateway — единая точка входа | — |
| `core/user-service` | CRUD пользователей | PostgreSQL `users` |
| `core/event-service` | События, категории, подборки, комментарии, лайки | PostgreSQL `main` |
| `core/request-service` | Заявки на участие в событиях | PostgreSQL `requests` |
| `stats/stats-server` | Статистика просмотров | PostgreSQL `stats` |
| `core/interaction-api` | Общие DTO для межсервисного взаимодействия | — |
| `stats/stats-client` | Feign-клиент для обращения к stats-server | — |
| `stats/stats-dto` | DTO сервиса статистики | — |

---

## Конфигурации

Все настройки хранятся централизованно в config-server:

```
infra/config-server/src/main/resources/config-repo/
├── gateway-server.yaml     # маршруты Gateway, настройки Eureka-клиента
├── event-service.yaml      # БД, Eureka, Feign, Resilience4j retry
├── request-service.yaml    # БД, Eureka, Feign
├── user-service.yaml       # БД, Eureka
└── stats-server.yaml       # БД, Eureka
```

Каждый сервис при старте подтягивает свой файл из config-server. Локально в `bootstrap.yaml` каждого сервиса указаны только адрес config-server и Eureka — всё остальное приходит централизованно.

### Маршруты Gateway

| Паттерн пути | Целевой сервис |
|---|---|
| `/admin/users`, `/admin/users/**` | `user-service` |
| `/users/*/requests`, `/users/*/requests/**` | `request-service` |
| `/users/*/events/*/requests` | `request-service` |
| `/users/**`, `/admin/**`, `/events/**`, `/categories/**`, `/compilations/**` | `event-service` |

---

## Внутренний API

Внутренние эндпоинты используются только для межсервисного взаимодействия через OpenFeign. Через Gateway они недоступны.

### `event-service` предоставляет

**`GET /internal/events/{eventId}`**

Вызывает: `request-service` — перед созданием заявки проверяет состояние события.

```json
{
  "id": 1,
  "initiatorId": 42,
  "state": "PUBLISHED",
  "participantLimit": 100,
  "requestModeration": true
}
```

---

### `user-service` предоставляет

**`GET /internal/users/{userId}`**

Вызывает: `event-service` — при создании события получает данные инициатора.

```json
{
  "id": 42,
  "name": "Иван Иванов",
  "email": "ivan@example.com"
}
```

---

### `request-service` предоставляет

Вызывает: `event-service` — для обогащения DTO событий полем `confirmedRequests`.

| Метод | Путь | Описание |
|-------|------|----------|
| `GET` | `/internal/requests/events/{eventId}/count` | Количество подтверждённых заявок для одного события |
| `GET` | `/internal/requests/confirmed-counts?eventIds=1,2,3` | Batch: `Map<eventId, count>` для списка событий — без проблемы N+1 |
| `GET` | `/internal/requests/confirmed?eventIds=1,2,3` | Список подтверждённых заявок для нескольких событий |
| `GET` | `/internal/requests/events/{eventId}?userId=` | Все заявки на событие (для владельца события) |
| `PATCH` | `/internal/requests/events/{eventId}?userId=&participantLimit=&requestModeration=` | Подтверждение / отклонение заявок владельцем |

**Пример:** `GET /internal/requests/confirmed-counts?eventIds=1,2,3`
```json
{ "1": 5, "2": 0, "3": 12 }
```

---

## Схемы взаимодействия

### Создание заявки на участие

```
POST /users/{userId}/requests?eventId={id}
  │
  Gateway → request-service
                │
                ├─ Feign GET /internal/events/{eventId} → event-service
                │         (проверка: событие опубликовано? лимит не превышен?)
                │
                └─ Сохранить заявку:
                   CONFIRMED — если requestModeration=false или participantLimit=0
                   PENDING   — если требуется ручная модерация
```

### Получение списка событий с confirmedRequests и views

```
GET /admin/events  или  GET /events
  │
  Gateway → event-service
                │
                ├─ 1. Получить список событий из ewm-db
                │
                ├─ 2. Feign GET /internal/requests/confirmed-counts?eventIds=...
                │         → request-service  (один batch-запрос на все события)
                │
                └─ 3. Feign GET /stats?uris=/events/1,/events/2,...
                          → stats-server  (один запрос для всех URI)
                          Собрать итоговые DTO: confirmedRequests и views заполнены
```

---

## Запуск

### Требования

- Docker и Docker Compose
- Свободные порты: `8080` (Gateway), `8761` (Eureka), `8888` (Config Server)

### Команды

```bash
# Первый запуск
docker compose up --build

# Остановка с удалением volumes (сброс БД)
docker compose down -v

# Полная пересборка
docker compose down -v && docker compose up --build
```

После запуска подождите ~60 секунд — сервисы регистрируются в Eureka постепенно.

### Проверка работоспособности

| URL | Что проверить |
|-----|---------------|
| http://localhost:8761 | Eureka Dashboard — все сервисы должны быть в статусе UP |
| http://localhost:8888/event-service/default | Конфиг event-service из Config Server |
| http://localhost:8080/actuator/health | Состояние Gateway |

---

## Внешний API

- **Основной сервис** (события, пользователи, заявки, категории, подборки):
  [`ewm-main-service-spec.json`](ewm-main-service-spec.json)

- **Сервис статистики** (запись хитов, получение статистики по URI):
  [`ewm-stats-service-spec.json`](ewm-stats-service-spec.json)

Спецификации в формате OpenAPI 3.0. Открыть можно через [Swagger Editor](https://editor.swagger.io/).