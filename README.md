# Explore With Me

Платформа для публикации мероприятий и записи на участие в них. Пользователи могут предлагать мероприятия, искать интересные события, оставлять заявки на участие и писать комментарии.

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

Проект построен на микросервисной архитектуре. Все внешние запросы поступают через единый API-шлюз на порту `8080`.

```
Клиент
  |
  ▼
Gateway (8080)
  |─► user-service    — управление пользователями (/admin/users)
  |─► request-service — заявки на участие в событиях (/users/*/requests)
  |─► main-service    — события, категории, подборки, комментарии
  └─► stats-server    — сбор и получение статистики просмотров
```

### Сервисы

| Сервис             | Описание                                                                  | База данных               | Порт (Docker) |
|--------------------|---------------------------------------------------------------------------|---------------------------|---------------|
| `discovery-server` | Eureka — реестр сервисов                                                  | —                         | 8761          |
| `config-server`    | Централизованное хранение конфигураций                                    | —                         | 8888          |
| `gateway-server`   | API-шлюз, маршрутизация запросов                                          | —                         | 8080          |
| `user-service`     | Управление пользователями: создание, получение, удаление                  | PostgreSQL (`user-db`)    | динамический  |
| `main-service`     | Основной сервис: события, категории, подборки, комментарии                | PostgreSQL (`ewm-db`)     | динамический  |
| `request-service`  | Управление заявками на участие в событиях                                 | PostgreSQL (`request-db`) | динамический  |
| `stats-server`     | Статистика просмотров событий                                             | PostgreSQL (`stats-db`)   | динамический  |

### Взаимодействие между сервисами

Сервисы общаются друг с другом через **OpenFeign** с балансировкой нагрузки через **Eureka**.

```
request-service → GET  /internal/events/{eventId}                → main-service
                        (получение данных о событии перед созданием заявки)

main-service    → GET  /internal/requests/events/{eventId}       → request-service
                → PATCH /internal/requests/events/{eventId}      → request-service
                → GET  /internal/requests/events/{eventId}/count → request-service
                → GET  /internal/requests/confirmed?eventIds=... → request-service

main-service    → GET  /internal/users/{userId}                  → user-service
                        (получение и локальное кэширование данных пользователя)
```

При недоступности одного из сервисов срабатывает **Resilience4j Circuit Breaker** — возвращаются безопасные значения по умолчанию (пустые списки, `0`, `null`), остальные функции продолжают работать.

Ошибки `4xx` от удалённых сервисов корректно преобразуются в локальные исключения через **FeignErrorDecoder** (например, `404` от `user-service` превращается в `NotFoundResource`).

### Расположение конфигураций

Конфигурации всех сервисов хранятся в `config-server` и загружаются при старте через Spring Cloud Config:

```
infra/config-server/src/main/resources/config-repo/
├- main-service.yaml    — настройки main-service (БД, Eureka, Resilience4j)
├- request-service.yaml — настройки request-service (БД, Eureka, Resilience4j)
├- user-service.yaml    — настройки user-service (БД, Eureka)
├- gateway-server.yaml  — маршруты Gateway
└- stats-server.yaml    — настройки stats-server
```

Каждый сервис подключается к `config-server` через `bootstrap.yaml` в своих ресурсах.

---

## Запуск

```bash
docker compose up --build
```

Сервисы поднимаются в правильном порядке автоматически (управляется через `depends_on`):

```
discovery-server
  └-► config-server
        ├-► gateway-server
        ├-► stats-server  ──────────────────────────────┐
        ├-► user-service  ──────────────────────────────┤
        └-► main-service (ждёт stats + user) ───────────┤
              └-► request-service                       ┘
```

После запуска подождите **1-2 минуты** пока все сервисы зарегистрируются в Eureka, затем:

| Сервис          | URL                       |
|-----------------|---------------------------|
| API             | http://localhost:8080     |
| Eureka Dashboard| http://localhost:8761     |
| Config Server   | http://localhost:8888     |

---

## Внешний API

Спецификация внешнего API (OpenAPI / Swagger):
- **Основной сервис**: [`ewm-main-service-spec.json`](ewm-main-service-spec.json)
- **Сервис статистики**: [`ewm-stats-service-spec.json`](ewm-stats-service-spec.json)

Все запросы отправляются через Gateway: `http://localhost:8080`

### Маршрутизация Gateway

Маршруты проверяются сверху вниз, первое совпадение побеждает:

| Паттерн                | Целевой сервис    | Описание                        |
|------------------------|-------------------|---------------------------------|
| `/admin/users`         | `user-service`    | Управление пользователями       |
| `/admin/users/**`      | `user-service`    | Управление пользователями       |
| `/users/*/requests`    | `request-service` | Заявки на участие               |
| `/users/*/requests/**` | `request-service` | Заявки на участие               |
| `/users/**`            | `main-service`    | Приватный API пользователей     |
| `/categories/**`       | `main-service`    | Категории событий               |
| `/admin/**`            | `main-service`    | Административный API            |
| `/events/**`           | `main-service`    | Публичный API событий           |
| `/compilations/**`     | `main-service`    | Подборки событий                |

---

## Внутренний API (межсервисное взаимодействие)

Эндпоинты **не доступны извне** — не маршрутизируются через Gateway. Используются только для взаимодействия между микросервисами.

### user-service → предоставляет

| Метод | Путь                       | Описание                                               |
|-------|----------------------------|--------------------------------------------------------|
| `GET` | `/internal/users/{userId}` | Получить данные пользователя (для main-service)        |

### main-service → предоставляет

| Метод | Путь                           | Описание                                               |
|-------|--------------------------------|--------------------------------------------------------|
| `GET` | `/internal/events/{eventId}`   | Получить данные события (для request-service)          |

### request-service → предоставляет

| Метод   | Путь                                           | Описание                                          |
|---------|------------------------------------------------|---------------------------------------------------|
| `GET`   | `/internal/requests/events/{eventId}?userId=`  | Заявки на событие (для владельца события)         |
| `PATCH` | `/internal/requests/events/{eventId}?userId=`  | Обновить статусы заявок                           |
| `GET`   | `/internal/requests/events/{eventId}/count`    | Количество подтверждённых заявок на событие       |
| `GET`   | `/internal/requests/confirmed?eventIds=`       | Батч-запрос подтверждённых заявок по списку событий|

---

## Публичный API (краткий справочник)

### Категории

| Метод    | Путь                        | Описание               |
|----------|-----------------------------|------------------------|
| `GET`    | `/categories`               | Список категорий       |
| `GET`    | `/categories/{catId}`       | Категория по ID        |
| `POST`   | `/admin/categories`         | Создать категорию      |
| `PATCH`  | `/admin/categories/{catId}` | Изменить категорию     |
| `DELETE` | `/admin/categories/{catId}` | Удалить категорию      |

### Пользователи

Запросы маршрутизируются на **user-service**.

| Метод    | Путь                    | Описание                  |
|----------|-------------------------|---------------------------|
| `GET`    | `/admin/users`          | Список пользователей      |
| `POST`   | `/admin/users`          | Создать пользователя      |
| `DELETE` | `/admin/users/{userId}` | Удалить пользователя      |

### События

| Метод    | Путь                                    | Описание                          |
|----------|-----------------------------------------|-----------------------------------|
| `GET`    | `/events`                               | Публичный поиск событий           |
| `GET`    | `/events/{id}`                          | Публичное получение события       |
| `GET`    | `/admin/events`                         | Поиск событий (admin)             |
| `PATCH`  | `/admin/events/{eventId}`               | Редактирование события (admin)    |
| `GET`    | `/users/{userId}/events`                | События пользователя              |
| `POST`   | `/users/{userId}/events`                | Создать событие                   |
| `GET`    | `/users/{userId}/events/{eventId}`      | Событие пользователя              |
| `PATCH`  | `/users/{userId}/events/{eventId}`      | Редактировать событие             |
| `GET`    | `/users/{userId}/events/{eventId}/requests` | Заявки на событие             |
| `PATCH`  | `/users/{userId}/events/{eventId}/requests` | Обновить статусы заявок       |

### Заявки на участие

Запросы маршрутизируются на **request-service**.

| Метод    | Путь                                          | Описание            |
|----------|-----------------------------------------------|---------------------|
| `GET`    | `/users/{userId}/requests`                    | Заявки пользователя |
| `POST`   | `/users/{userId}/requests?eventId=`           | Подать заявку       |
| `PATCH`  | `/users/{userId}/requests/{requestId}/cancel` | Отменить заявку     |

### Подборки

| Метод    | Путь                            | Описание               |
|----------|---------------------------------|------------------------|
| `GET`    | `/compilations`                 | Список подборок        |
| `GET`    | `/compilations/{compId}`        | Подборка по ID         |
| `POST`   | `/admin/compilations`           | Создать подборку       |
| `PATCH`  | `/admin/compilations/{compId}`  | Редактировать подборку |
| `DELETE` | `/admin/compilations/{compId}`  | Удалить подборку       |

### Комментарии (дополнительная функциональность)

| Метод    | Путь                                          | Описание                          |
|----------|-----------------------------------------------|-----------------------------------|
| `GET`    | `/events/{eventId}/comments`                  | Комментарии к событию (публично)  |
| `GET`    | `/users/{userId}/comments`                    | Все комментарии пользователя      |
| `GET`    | `/users/{userId}/comments/{commentId}`        | Конкретный комментарий            |
| `POST`   | `/users/{userId}/comments/events/{eventId}`   | Оставить комментарий              |
| `PATCH`  | `/users/{userId}/comments/{commentId}`        | Редактировать комментарий         |
| `DELETE` | `/users/{userId}/comments/{commentId}`        | Удалить комментарий               |

---

## Автор

**Гарданов Апти** — [github.com/bardochello](https://github.com/bardochello)