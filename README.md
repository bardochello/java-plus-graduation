# Explore With Me
Платформа для публикации мероприятий и записи на участие на них. Пользователи могут предлагать мероприятия, искать интересные событияя, оставлять заявки на участие и писать комментарии.

---

## Содержание
 
- [Архитектура](#архитектура)
  - [Сервисы](#сервисы)
  - [Взаимодействие между сервисами](#взаимодействие-между-сервисами)
  - [Расположение конфигураций](#расположение-конфигураций)
- [Запуск](#запуск)
- [Внешний API](#внешний-api)
  - [Маршрутизация Gateway](#маршрутизация-gateway)
- [Внутренний API](#внутренний-api-межсервисное-взаимодействие)
- [Публичный API](#публичный-api-краткий-справочник)
  - [Категории](#категории)
  - [Пользователи](#пользователи)
  - [События](#события)
  - [Заявки на участие](#заявки-на-участие)
  - [Подборки](#подборки)
  - [Комментарии](#комментарии-дополнительная-функциональность)
- [Автор](#автор)
---

## Архитектура
Проект построен на микросервисной архитектуре. Все внешние запросы поступают через единый API-шлюз на порту "8080".

```
Клиент
  |
  ▼
Gateway (8080)
  |─► main-service     — события, категории, пользователи, подборки, комментарии
  ├─► request-service  — заявки на участие в событиях
  └─► stats-server     — сбор и получение статистики просмотров

```

### Сервисы

| Сервис            | Описание                                                                 | База Данных               |
|-------------------|--------------------------------------------------------------------------|---------------------------|
| `discovery-server` | Eureka - реестр сервисов                                                 | -                         |
| `config-server`   | Централизованное хранение конфигураций                                   | -                         |
| `gateway-server`  | API-шлюз, маршрутизация запросов                                         | -                         |
| `main-service`    | Основной сервис: события, категории, пользователи, подборки, комментарии | PostgreSQL (`ewm-db`)     |
| `request-service` | Управление заявками на участие в событиях                                | PostgreSQL (`request-db`) |
| `stats-server`    | Статистика просмотров событий                                            | PostgreSQL (`stats-db`)   |

### Взаимодействие между сервисами
Сервисы общаются друг с другом через **OpenFeign** с балансировкой нагрузки через **Eureka**.

```
request-service - GET /internal/events/{eventId} -> main-service
                    (получение данных о событии перед созданием заявки)

main-service    - GET /internal/requests/events/{eventId} -> request-service
                - PATCH /internal/requests/events/{eventId} -> request-service
                - GET /internal/requests/events/{eventId}/count -> request-service
                - GET /internal/requests/confirmed?eventIds=... -> request-service
```
При недоступности одного из сервисов срабатывает **esilience4j Circuit Breaker** - возвращаются безопасные значения по умолчанию (пустые списки, `0`), остальные функии продолжают работать.

### Расположение конфигураций
Конфигурации всех сервисов хранятся в `config-server` и загружаются при старте через Spring Cloud Config:

```
infra/config-server/src/main/resources/config-repo/
├─ main-service.yaml      — настройки main-service (БД, Eureka, Resilience4j)
├─ request-service.yaml   — настройки request-service (БД, Eureka, Resilience4j)
├─ gateway-server.yaml    — маршруты Gateway
└─ stats-server.yaml      — настройки stats-server
```
Каждый сервис подключается к config-server через `bootstrap.yaml` в своих ресурсах.

---

## Запуск

```bash
  docker compose up --build
```

Сервисы поднимаются в правильном порядке автоматически:

```
discovery-server -> config-server -> gateway-server
                                  -> stats-server -> main-service -> request-service
```

После запуска:
 - **API**: `http://localhost:8080`
 - **Eureka Dashboard**: `http://localhost:8761`
 - **Config Server** `https://localhost:8888`

---
После запуска:
- **API**: `http://localhost:8080`
- **Eureka Dashboard**: `http://localhost:8761`
- **Config Server**: `http://localhost:8888`
---
 
## Внешний API
 
Спецификация внешнего API (OpenAPI / Swagger):
- **Основной сервис**: [`ewm-main-service-spec.json`](ewm-main-service-spec.json)
- **Сервис статистики**: [`ewm-stats-service-spec.json`](ewm-stats-service-spec.json)
Все запросы отправляются на Gateway: `http://localhost:8080`
 
### Маршрутизация Gateway
 
| Паттерн | Сервис |
|---|---|
| `/users/*/requests` | `request-service` |
| `/users/*/requests/**` | `request-service` |
| `/users/**` | `main-service` |
| `/categories/**` | `main-service` |
| `/admin/**` | `main-service` |
| `/events/**` | `main-service` |
| `/compilations/**` | `main-service` |
 
---
 
## Внутренний API (межсервисное взаимодействие)
 
Эндпоинты не доступны извне (не маршрутизируются через Gateway).
 
### main-service → предоставляет для request-service
 
| Метод | Путь | Описание |
|---|---|---|
| `GET` | `/internal/events/{eventId}` | Получить данные события (статус, лимит, модерация, инициатор) |
 
### request-service → предоставляет для main-service
 
| Метод | Путь | Описание |
|---|---|---|
| `GET` | `/internal/requests/events/{eventId}?userId=` | Получить заявки на событие (для владельца) |
| `PATCH` | `/internal/requests/events/{eventId}?userId=` | Обновить статусы заявок |
| `GET` | `/internal/requests/events/{eventId}/count` | Количество подтверждённых заявок |
| `GET` | `/internal/requests/confirmed?eventIds=` | Батч-запрос подтверждённых заявок для списка событий |
 
---
 
## Публичный API (краткий справочник)
 
### Категории
| Метод | Путь | Описание |
|---|---|---|
| `GET` | `/categories` | Список категорий |
| `GET` | `/categories/{catId}` | Категория по ID |
| `POST` | `/admin/categories` | Создать категорию |
| `PATCH` | `/admin/categories/{catId}` | Изменить категорию |
| `DELETE` | `/admin/categories/{catId}` | Удалить категорию |
 
### Пользователи
| Метод | Путь | Описание |
|---|---|---|
| `GET` | `/admin/users` | Список пользователей |
| `POST` | `/admin/users` | Создать пользователя |
| `DELETE` | `/admin/users/{userId}` | Удалить пользователя |
 
### События
| Метод | Путь | Описание |
|---|---|---|
| `GET` | `/events` | Публичный поиск событий |
| `GET` | `/events/{id}` | Публичное получение события |
| `GET` | `/admin/events` | Поиск событий (admin) |
| `PATCH` | `/admin/events/{eventId}` | Редактирование события (admin) |
| `GET` | `/users/{userId}/events` | События пользователя |
| `POST` | `/users/{userId}/events` | Создать событие |
| `GET` | `/users/{userId}/events/{eventId}` | Событие пользователя |
| `PATCH` | `/users/{userId}/events/{eventId}` | Редактировать событие |
| `GET` | `/users/{userId}/events/{eventId}/requests` | Заявки на событие |
| `PATCH` | `/users/{userId}/events/{eventId}/requests` | Обновить статусы заявок |
 
### Заявки на участие
| Метод | Путь | Описание |
|---|---|---|
| `GET` | `/users/{userId}/requests` | Заявки пользователя |
| `POST` | `/users/{userId}/requests` | Подать заявку |
| `PATCH` | `/users/{userId}/requests/{requestId}/cancel` | Отменить заявку |
 
### Подборки
| Метод | Путь | Описание |
|---|---|---|
| `GET` | `/compilations` | Список подборок |
| `GET` | `/compilations/{compId}` | Подборка по ID |
| `POST` | `/admin/compilations` | Создать подборку |
| `PATCH` | `/admin/compilations/{compId}` | Редактировать подборку |
| `DELETE` | `/admin/compilations/{compId}` | Удалить подборку |
 
### Комментарии (дополнительная функциональность)
| Метод | Путь | Описание |
|---|---|---|
| `GET` | `/events/{eventId}/comments` | Комментарии к событию (публично) |
| `GET` | `/users/{userId}/comments` | Комментарии пользователя |
| `GET` | `/users/{userId}/comments/{commentId}` | Конкретный комментарий |
| `POST` | `/users/{userId}/comments/events/{eventId}` | Оставить комментарий |
| `PATCH` | `/users/{userId}/comments/{commentId}` | Редактировать комментарий |
| `DELETE` | `/users/{userId}/comments/{commentId}` | Удалить комментарий |
 
---
## Автор
**Гарданов Апти** - [github.com/bardochello](https://github.com/bardochello)