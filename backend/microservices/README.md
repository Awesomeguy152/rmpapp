# Микросервисная архитектура RMP App

## Обзор

Приложение разбито на **5 микросервисов**, взаимодействующих через **Redis/KeyDB** как брокер сообщений.

## Архитектура

```
┌─────────────────────────────────────────────────────────────────────┐
│                        API Gateway (Nginx)                          │
│                         Port: 80/443                                │
└─────────────────┬───────────────────────────────────────────────────┘
                  │
    ┌─────────────┼─────────────┬─────────────┬─────────────┐
    │             │             │             │             │
    ▼             ▼             ▼             ▼             ▼
┌───────┐   ┌───────┐   ┌───────┐   ┌───────┐   ┌───────────┐
│ User  │   │ Chat  │   │  AI   │   │ Push  │   │ Analytics │
│Service│   │Service│   │Service│   │Service│   │  Service  │
│ :8081 │   │ :8082 │   │ :8083 │   │ :8084 │   │   :8085   │
└───┬───┘   └───┬───┘   └───┬───┘   └───┬───┘   └─────┬─────┘
    │           │           │           │             │
    └───────────┴───────────┴───────────┴─────────────┘
                            │
                    ┌───────▼───────┐
                    │  Redis/KeyDB  │
                    │ Message Broker│
                    │    :6379      │
                    └───────┬───────┘
                            │
              ┌─────────────┼─────────────┐
              │             │             │
        ┌─────▼─────┐ ┌─────▼─────┐ ┌─────▼─────┐
        │ PostgreSQL│ │ClickHouse │ │  Firebase │
        │  (Main)   │ │(Analytics)│ │  (Push)   │
        │   :5432   │ │   :8443   │ │           │
        └───────────┘ └───────────┘ └───────────┘
```

## Микросервисы

### 1. User Service (порт 8081)
**Ответственность:** Управление пользователями и аутентификация
- Регистрация/логин
- JWT токены
- Профили пользователей
- Сброс пароля

**Redis каналы:**
- `microservice:users` - события пользователей

### 2. Chat Service (порт 8082)
**Ответственность:** Чаты и сообщения
- CRUD чатов
- Отправка/получение сообщений
- WebSocket real-time
- Архивация чатов

**Redis каналы:**
- `microservice:chats` - события чатов

### 3. AI Service (порт 8083)
**Ответственность:** AI-ассистент
- OpenAI GPT интеграция
- Генерация ответов
- История диалогов с AI

**Redis каналы:**
- `microservice:ai` - запросы к AI

### 4. Notification Service (порт 8084)
**Ответственность:** Push-уведомления
- Firebase Cloud Messaging
- Токены устройств
- Рассылка уведомлений

**Redis каналы:**
- `microservice:notifications` - события уведомлений

### 5. Analytics Service (порт 8085)
**Ответственность:** Аналитика и мониторинг
- ClickHouse интеграция
- Логирование запросов
- Метрики производительности
- Статистика использования

**Redis каналы:**
- `microservice:analytics` - события аналитики

## Взаимодействие через Redis

```kotlin
// Публикация события
redisService.publish("microservice:users", """
    {"type": "user_registered", "userId": "123", "timestamp": 1234567890}
""")

// Подписка на события
redisService.subscribe("microservice:chats") { channel, message ->
    println("Received on $channel: $message")
}
```

## Запуск

### Разработка (монолит)
```bash
./gradlew run
```

### Production (микросервисы)
```bash
docker-compose -f docker-compose.microservices.yml up
```

## Переменные окружения

| Переменная | Описание | Пример |
|------------|----------|--------|
| SERVICE_NAME | Имя микросервиса | user-service |
| SERVICE_PORT | Порт сервиса | 8081 |
| REDIS_URL | URL Redis/KeyDB | redis://localhost:6379 |
| DATABASE_URL | PostgreSQL URL | postgresql://... |
| CLICKHOUSE_URL | ClickHouse URL | jdbc:clickhouse://... |

## Масштабирование

Каждый микросервис может быть масштабирован независимо:

```yaml
services:
  user-service:
    deploy:
      replicas: 3
  chat-service:
    deploy:
      replicas: 5
```
