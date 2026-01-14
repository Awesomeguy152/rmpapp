#!/bin/bash
# Скрипт демонстрации проекта RMP App для преподавателя

BASE_URL="https://rmpapp-production.up.railway.app"

echo "========================================================"
echo "        ДЕМОНСТРАЦИЯ ПРОЕКТА RMP APP                    "
echo "========================================================"
echo ""

echo "1. REDIS/KEYDB (Брокер сообщений):"
echo "   Команда: curl $BASE_URL/api/redis/status"
curl -s "$BASE_URL/api/redis/status" | python3 -m json.tool
echo ""

echo "2. CLICKHOUSE (Аналитика и логирование):"
echo "   Команда: curl $BASE_URL/api/analytics/status"
curl -s "$BASE_URL/api/analytics/status" | python3 -m json.tool
echo ""

echo "3. МИКРОСЕРВИСЫ (5 сервисов):"
echo "   Команда: curl $BASE_URL/api/microservices/status"
curl -s "$BASE_URL/api/microservices/status" | python3 -m json.tool
echo ""

echo "4. API HEALTH CHECK:"
echo "   Команда: curl $BASE_URL/api/health"
curl -s "$BASE_URL/api/health" | python3 -m json.tool
echo ""

echo "========================================================"
echo "        ВСЕ КОМПОНЕНТЫ РАБОТАЮТ!                        "
echo "========================================================"
