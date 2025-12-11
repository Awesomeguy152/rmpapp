# SRS — Мини‑мессенджер (Server)

## Назначение
Упрощение деловой коммуникации, сканирование диалогов ИИ (встречи/напоминания), снижение шума.

## Функциональные требования (MVP)
- JWT‑регистрация и логин (ADMIN/USER).
- Профиль `/api/me`.
- Админ‑список пользователей.
(дальнейшие требования: сообщения/чаты/Redis/WebRTC — см. раздел План).

## НФТ
- Цель 2000 msg/s (последующие этапы).
- Безопасность: JWT HS256, BCrypt(12), HTTPS за прокси.
- Масштабирование: stateless + БД, брокер Redis (этап 2).

## Архитектура
Android App ↔ Ktor Server ↔ PostgreSQL, Redis (этап 2), WebRTC signaling (этап 2).

## Развёртывание
Dockerfile (multi‑stage), docker‑compose. ENV: DB_URL, DB_USER, DB_PASSWORD, JWT_SECRET, JWT_ISSUER, JWT_AUDIENCE, PORT.

## Приёмочные сценарии
1) Register → 201; запись в БД `users`.
2) Login → 200 {token}; `/api/me` → 200.
3) `/api/admin/users` доступен только с ролью ADMIN.
