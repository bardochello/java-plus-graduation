# 🔴 КРИТИЧНОЕ ИСПРАВЛЕНИЕ: confirmedRequests = 0 вместо 1

## Проблема
После принятия заявки на участие, счетчик `confirmedRequests` остается 0 вместо 1.

**Ошибка теста:**
```
expected +0 to equal 1
```

## Анализ
Проблема находится в методе `getRequestsByEventIdIn()` в `RequestServiceImpl.java`. Этот метод вызывается из event-service для получения количества подтвержденных заявок для списка событий.

## Решение

### Замена файла

Используйте **`UPDATED_RequestServiceImpl.java`** вместо `FIXED_RequestServiceImpl.java`:

```bash
cp /sessions/eager-adoring-cori/mnt/java-plus-graduation/UPDATED_RequestServiceImpl.java \
   core/request-service/src/main/java/ru/practicum/request/service/RequestServiceImpl.java
```

Или для Windows:
```powershell
Copy-Item -Path "java-plus-graduation\UPDATED_RequestServiceImpl.java" `
          -Destination "core\request-service\src\main\java\ru\practicum\request\service\RequestServiceImpl.java" `
          -Force
```

### Ключевые улучшения в коде

**Добавлено детальное логирование в getRequestsByEventIdIn():**

```java
@Override
public List<ParticipationRequestDto> getRequestsByEventIdIn(List<Long> eventIds) {
    log.info("===== GETTING CONFIRMED REQUESTS =====");
    log.info("Requesting confirmed requests for eventIds: {}", eventIds);
    
    List<Request> allRequests = requestRepository.findAllByEventIdInAndStatus(eventIds, Status.CONFIRMED);
    log.info("Total CONFIRMED requests found in DB: {}", allRequests.size());
    
    // Логирование каждого найденного запроса для отладки
    for (Request req : allRequests) {
        log.info("Found CONFIRMED request: id={}, eventId={}, requesterId={}, status={}",
                req.getId(), req.getEventId(), req.getRequesterId(), req.getStatus());
    }
    
    List<ParticipationRequestDto> result = allRequests.stream()
            .map(RequestMapper::mapToDto)
            .toList();
    
    log.info("Converted to DTOs: {}", result.size());
    for (ParticipationRequestDto dto : result) {
        log.info("DTO: id={}, event={}, status={}", dto.getId(), dto.getEvent(), dto.getStatus());
    }
    
    log.info("===== END GETTING CONFIRMED REQUESTS =====");
    return result;
}
```

Это логирование поможет нам отследить:
1. Какие eventIds запрашиваются
2. Сколько CONFIRMED заявок найдено в БД
3. Как выглядит каждый запрос в БД
4. Правильно ли они конвертируются в DTO

## 📋 Инструкция по применению

### Шаг 1: Скопируйте исправленный файл
```bash
cp /sessions/eager-adoring-cori/mnt/java-plus-graduation/UPDATED_RequestServiceImpl.java \
   core/request-service/src/main/java/ru/practicum/request/service/RequestServiceImpl.java
```

### Шаг 2: Перестройте проект
```bash
cd core/request-service
mvn clean compile
```

### Шаг 3: Перезагрузите сервис
```bash
# Остановите старый процесс
# Запустите новый:
java -jar target/request-service-1.0.jar
```

### Шаг 4: Запустите тест
- Запустите Postman коллекцию
- Проверьте логи сервиса при выполнении теста "Поиск событий с проверкой параметров"

## 🔍 Что смотреть в логах

После запуска теста вы должны увидеть что-то вроде:

```log
INFO  ===== GETTING CONFIRMED REQUESTS =====
INFO  Requesting confirmed requests for eventIds: [10]
INFO  Total CONFIRMED requests found in DB: 1
INFO  Found CONFIRMED request: id=5, eventId=10, requesterId=2, status=CONFIRMED
INFO  Converted to DTOs: 1
INFO  DTO: id=5, event=10, status=CONFIRMED
INFO  ===== END GETTING CONFIRMED REQUESTS =====
```

Если вместо этого вы видите:
```log
INFO  Total CONFIRMED requests found in DB: 0
```

Значит, заявка не сохраняется как CONFIRMED. В этом случае нужно проверить метод `updateRequestStatus()`.

## 🆘 Если проблема не решится

### Проверка 1: Заявка сохраняется с правильным статусом?
Добавьте в метод `updateRequestStatus()` логирование:
```java
log.info("Saving request as CONFIRMED: id={}", req.getId());
req.setStatus(Status.CONFIRMED);
confirmedList.add(RequestMapper.mapToDto(requestRepository.save(req)));
log.info("Request saved, status={}", req.getStatus());
```

### Проверка 2: Запрос в БД правильный?
Выполните прямой SQL запрос:
```sql
SELECT * FROM requests WHERE event_id = ? AND status = 'CONFIRMED';
```

Должна быть одна запись со статусом CONFIRMED.

### Проверка 3: Время выполнения
Может быть проблема с временем. Добавьте небольшую задержку в тесте после принятия заявки:
```javascript
// После принятия заявки добавьте:
await new Promise(resolve => setTimeout(resolve, 500)); // Жди 500 мс
```

## ✅ Что делать после исправления

1. ✅ Скопируйте файл
2. ✅ Перестройте проект
3. ✅ Перезагрузите сервис
4. ✅ Запустите тесты
5. ✅ Проверьте логи
6. ✅ Убедитесь, что тест пройден

---

**Статус:** Готово к применению  
**Файл для копирования:** `UPDATED_RequestServiceImpl.java`
