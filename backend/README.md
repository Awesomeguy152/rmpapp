### How to run

```bash
docker compose up --build
```

Smoke test:

```bash
curl -X POST http://localhost:8080/api/auth/register -H "Content-Type: application/json" -d '{"email":"admin@example.com","password":"admin","role":"ADMIN"}'
curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"email":"admin@example.com","password":"admin"}'
```

### Admin registration

Чтобы создать администратора, необходимо задать переменную окружения `ADMIN_REGISTRATION_SECRET` на сервере и передать её значение в `adminSecret` при вызове `POST /api/auth/register`.

Пример запроса:

```bash
curl -X POST http://localhost:8080/api/auth/register \
	-H "Content-Type: application/json" \
	-d '{
				"email":"admin@example.com",
				"password":"admin",
				"role":"ADMIN",
				"adminSecret":"super-secret"
			}'
```

Если `ADMIN_REGISTRATION_SECRET` не задан или секрет не совпадает, создания администратора не произойдёт.

### Chat API (Sprint 1+)

Диалоги и управление участниками:

| Метод    | URL                                               | Описание                                                                           |
| -------- | ------------------------------------------------- | ---------------------------------------------------------------------------------- |
| `GET`    | `/api/chat/conversations`                         | Лента диалогов/групп с последним сообщением и счётчиком непрочитанных.             |
| `POST`   | `/api/chat/conversations/direct`                  | Создаёт (или возвращает существующий) приватный диалог между двумя пользователями. |
| `POST`   | `/api/chat/conversations/group`                   | Создаёт новую группу, добавляя список участников.                                  |
| `PATCH`  | `/api/chat/conversations/{id}/topic`              | Переименовывает группу (доступно владельцу).                                       |
| `POST`   | `/api/chat/conversations/{id}/members`            | Добавляет участников в группу.                                                     |
| `DELETE` | `/api/chat/conversations/{id}/members/{memberId}` | Удаляет участника (владелец или сам участник).                                     |
| `POST`   | `/api/chat/conversations/{id}/read`               | Фиксирует прочитанные сообщения (по последнему `messageId`).                       |

Сообщения, теги и вложения:

| Метод    | URL                                     | Описание                                                                       |
| -------- | --------------------------------------- | ------------------------------------------------------------------------------ |
| `POST`   | `/api/chat/conversations/{id}/messages` | Отправляет сообщение с опциональными вложениями (Base64).                      |
| `GET`    | `/api/chat/conversations/{id}/messages` | История с фильтрами `only` (`meetings`, `answers`, `important`) и поиском `q`. |
| `PATCH`  | `/api/chat/messages/{id}`               | Редактирует текст и/или вложения (доступно автору).                            |
| `DELETE` | `/api/chat/messages/{id}`               | Помечает сообщение удалённым (возвращает пустой текст и убирает вложения).     |
| `POST`   | `/api/chat/messages/{id}/tag`           | Назначает тег (`NONE`, `ANSWER`, `MEETING`, `IMPORTANT`).                      |
| `GET`    | `/api/chat/messages`                    | Глобальный поиск сообщений пользователя по тегам и ключевым словам.            |

Любые примеры смотри в `openapi.yaml` или через Swagger UI (`/docs`).

### AI ассистент (заглушки)

Доступны ручки, которые вовремя интеграции можно привязать к OpenAI или другой LLM:

- `POST /api/ai/summary`
- `POST /api/ai/next-action`

Сейчас ответы формируются заглушкой `AiAssistantService`, но структура уже готова.

### Документация

- OpenAPI: http://localhost:8080/openapi.yaml
- Swagger UI: http://localhost:8080/docs

Файл спецификации лежит в корне (`openapi.yaml`).
