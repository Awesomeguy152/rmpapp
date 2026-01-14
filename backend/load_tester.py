#!/usr/bin/env python3
"""
🔥 RMP App Load Tester — Имитатор 10,000 клиентов

Скрипт для нагрузочного тестирования API согласно требованиям курса.
Имитирует одновременную работу 10,000 пользователей.

Использование:
    python load_tester.py --url https://rmpapp-production.up.railway.app --clients 10000

Требования:
    pip install aiohttp asyncio argparse
"""

import asyncio
import aiohttp
import argparse
import time
import random
import string
import json
from dataclasses import dataclass, field
from typing import List, Dict, Optional
from collections import defaultdict
import statistics

# ===== Конфигурация =====

@dataclass
class TestConfig:
    base_url: str = "http://localhost:8080"
    total_clients: int = 10000
    concurrent_clients: int = 500  # Одновременных подключений
    requests_per_client: int = 10
    ramp_up_seconds: int = 30  # Время разогрева
    test_duration_seconds: int = 60
    timeout_seconds: int = 30

@dataclass
class TestResult:
    endpoint: str
    method: str
    status_code: int
    duration_ms: float
    success: bool
    error: Optional[str] = None
    timestamp: float = field(default_factory=time.time)

@dataclass
class TestSummary:
    total_requests: int = 0
    successful_requests: int = 0
    failed_requests: int = 0
    total_duration_ms: float = 0
    min_duration_ms: float = float('inf')
    max_duration_ms: float = 0
    durations: List[float] = field(default_factory=list)
    status_codes: Dict[int, int] = field(default_factory=lambda: defaultdict(int))
    errors: Dict[str, int] = field(default_factory=lambda: defaultdict(int))
    
    def add_result(self, result: TestResult):
        self.total_requests += 1
        self.total_duration_ms += result.duration_ms
        self.durations.append(result.duration_ms)
        self.status_codes[result.status_code] += 1
        
        if result.success:
            self.successful_requests += 1
            self.min_duration_ms = min(self.min_duration_ms, result.duration_ms)
            self.max_duration_ms = max(self.max_duration_ms, result.duration_ms)
        else:
            self.failed_requests += 1
            if result.error:
                self.errors[result.error] += 1
    
    @property
    def avg_duration_ms(self) -> float:
        return self.total_duration_ms / self.total_requests if self.total_requests > 0 else 0
    
    @property
    def p50_ms(self) -> float:
        return statistics.median(self.durations) if self.durations else 0
    
    @property
    def p95_ms(self) -> float:
        if not self.durations:
            return 0
        sorted_durations = sorted(self.durations)
        idx = int(len(sorted_durations) * 0.95)
        return sorted_durations[idx]
    
    @property
    def p99_ms(self) -> float:
        if not self.durations:
            return 0
        sorted_durations = sorted(self.durations)
        idx = int(len(sorted_durations) * 0.99)
        return sorted_durations[idx]
    
    @property
    def success_rate(self) -> float:
        return (self.successful_requests / self.total_requests * 100) if self.total_requests > 0 else 0
    
    @property
    def rps(self) -> float:
        if not self.durations:
            return 0
        total_time_s = sum(self.durations) / 1000
        return self.total_requests / max(total_time_s / len(self.durations) * self.total_requests / 100, 0.001)

# ===== Генераторы тестовых данных =====

def random_string(length: int = 10) -> str:
    return ''.join(random.choices(string.ascii_lowercase + string.digits, k=length))

def random_email() -> str:
    return f"loadtest_{random_string(8)}@test.com"

def random_username() -> str:
    return f"user_{random_string(6)}"

# ===== Клиент для тестирования =====

class LoadTestClient:
    def __init__(self, config: TestConfig, client_id: int, summary: TestSummary):
        self.config = config
        self.client_id = client_id
        self.summary = summary
        self.token: Optional[str] = None
        self.user_id: Optional[str] = None
        
    async def make_request(
        self, 
        session: aiohttp.ClientSession, 
        method: str, 
        endpoint: str, 
        data: Optional[dict] = None,
        headers: Optional[dict] = None
    ) -> TestResult:
        url = f"{self.config.base_url}{endpoint}"
        req_headers = headers or {}
        if self.token:
            req_headers["Authorization"] = f"Bearer {self.token}"
        
        start_time = time.time()
        try:
            async with session.request(
                method, 
                url, 
                json=data, 
                headers=req_headers,
                timeout=aiohttp.ClientTimeout(total=self.config.timeout_seconds)
            ) as response:
                duration_ms = (time.time() - start_time) * 1000
                await response.text()  # Read response body
                
                result = TestResult(
                    endpoint=endpoint,
                    method=method,
                    status_code=response.status,
                    duration_ms=duration_ms,
                    success=200 <= response.status < 400
                )
                self.summary.add_result(result)
                return result
                
        except asyncio.TimeoutError:
            duration_ms = (time.time() - start_time) * 1000
            result = TestResult(
                endpoint=endpoint,
                method=method,
                status_code=0,
                duration_ms=duration_ms,
                success=False,
                error="Timeout"
            )
            self.summary.add_result(result)
            return result
            
        except Exception as e:
            duration_ms = (time.time() - start_time) * 1000
            result = TestResult(
                endpoint=endpoint,
                method=method,
                status_code=0,
                duration_ms=duration_ms,
                success=False,
                error=str(type(e).__name__)
            )
            self.summary.add_result(result)
            return result
    
    async def run_scenario(self, session: aiohttp.ClientSession):
        """Сценарий поведения одного пользователя"""
        
        # 1. Регистрация
        email = random_email()
        username = random_username()
        password = "TestPass123!"
        
        await self.make_request(session, "POST", "/api/auth/register", {
            "email": email,
            "username": username,
            "password": password
        })
        
        # 2. Логин
        result = await self.make_request(session, "POST", "/api/auth/login", {
            "email": email,
            "password": password
        })
        
        # Небольшая пауза между запросами
        await asyncio.sleep(random.uniform(0.1, 0.5))
        
        # 3. Получение чатов
        await self.make_request(session, "GET", "/api/chats")
        
        # 4. Создание чата
        await self.make_request(session, "POST", "/api/chats", {
            "title": f"Load Test Chat {self.client_id}"
        })
        
        await asyncio.sleep(random.uniform(0.1, 0.3))
        
        # 5. Отправка сообщений
        for i in range(3):
            await self.make_request(session, "GET", "/api/chats")
            await asyncio.sleep(random.uniform(0.05, 0.2))
        
        # 6. Проверка профиля
        await self.make_request(session, "GET", "/api/profile")
        
        # 7. Статус аналитики (публичный)
        await self.make_request(session, "GET", "/api/analytics/status")

# ===== Основной тестер =====

class LoadTester:
    def __init__(self, config: TestConfig):
        self.config = config
        self.summary = TestSummary()
        self.start_time: float = 0
        self.end_time: float = 0
        
    async def run_client(self, session: aiohttp.ClientSession, client_id: int):
        client = LoadTestClient(self.config, client_id, self.summary)
        await client.run_scenario(session)
    
    async def run(self):
        print(f"""
╔══════════════════════════════════════════════════════════════════╗
║          🔥 RMP App Load Tester — 10,000 Clients                ║
╠══════════════════════════════════════════════════════════════════╣
║  Target URL:      {self.config.base_url:<43} ║
║  Total Clients:   {self.config.total_clients:<43} ║
║  Concurrent:      {self.config.concurrent_clients:<43} ║
║  Ramp-up Time:    {self.config.ramp_up_seconds} seconds{' ' * 33} ║
╚══════════════════════════════════════════════════════════════════╝
""")
        
        connector = aiohttp.TCPConnector(
            limit=self.config.concurrent_clients,
            limit_per_host=self.config.concurrent_clients,
            ttl_dns_cache=300,
            enable_cleanup_closed=True
        )
        
        self.start_time = time.time()
        
        async with aiohttp.ClientSession(connector=connector) as session:
            # Постепенный запуск клиентов (ramp-up)
            batch_size = self.config.total_clients // self.config.ramp_up_seconds
            if batch_size == 0:
                batch_size = self.config.total_clients
            
            tasks = []
            clients_started = 0
            
            for batch_num in range(0, self.config.total_clients, batch_size):
                batch_end = min(batch_num + batch_size, self.config.total_clients)
                
                for client_id in range(batch_num, batch_end):
                    task = asyncio.create_task(self.run_client(session, client_id))
                    tasks.append(task)
                    clients_started += 1
                
                # Прогресс
                progress = clients_started / self.config.total_clients * 100
                print(f"\r⏳ Starting clients: {clients_started}/{self.config.total_clients} ({progress:.1f}%)", end="")
                
                if batch_end < self.config.total_clients:
                    await asyncio.sleep(1)  # Пауза между batch'ами
            
            print(f"\n\n🚀 All {self.config.total_clients} clients started! Waiting for completion...\n")
            
            # Ожидаем завершения всех задач
            await asyncio.gather(*tasks, return_exceptions=True)
        
        self.end_time = time.time()
        self.print_results()
    
    def print_results(self):
        total_time = self.end_time - self.start_time
        
        print(f"""
╔══════════════════════════════════════════════════════════════════╗
║                    📊 РЕЗУЛЬТАТЫ ТЕСТИРОВАНИЯ                    ║
╠══════════════════════════════════════════════════════════════════╣
║  ⏱️  Общее время теста:       {total_time:>10.2f} секунд                ║
║  📨 Всего запросов:          {self.summary.total_requests:>10}                      ║
║  ✅ Успешных:                {self.summary.successful_requests:>10}                      ║
║  ❌ Неудачных:               {self.summary.failed_requests:>10}                      ║
║  📈 Успешность:              {self.summary.success_rate:>10.2f}%                     ║
╠══════════════════════════════════════════════════════════════════╣
║                       ⚡ ПРОИЗВОДИТЕЛЬНОСТЬ                       ║
╠══════════════════════════════════════════════════════════════════╣
║  🔄 Requests/sec (RPS):      {self.summary.total_requests / total_time:>10.2f}                      ║
║  ⏱️  Среднее время:          {self.summary.avg_duration_ms:>10.2f} ms                   ║
║  ⏱️  Минимальное:            {self.summary.min_duration_ms if self.summary.min_duration_ms != float('inf') else 0:>10.2f} ms                   ║
║  ⏱️  Максимальное:           {self.summary.max_duration_ms:>10.2f} ms                   ║
║  📊 P50 (медиана):           {self.summary.p50_ms:>10.2f} ms                   ║
║  📊 P95:                     {self.summary.p95_ms:>10.2f} ms                   ║
║  📊 P99:                     {self.summary.p99_ms:>10.2f} ms                   ║
╠══════════════════════════════════════════════════════════════════╣
║                        📋 HTTP СТАТУСЫ                           ║
╠══════════════════════════════════════════════════════════════════╣""")
        
        for status, count in sorted(self.summary.status_codes.items()):
            status_name = self._get_status_name(status)
            print(f"║  {status:>3} {status_name:<20}     {count:>10} запросов          ║")
        
        if self.summary.errors:
            print("""╠══════════════════════════════════════════════════════════════════╣
║                          ⚠️  ОШИБКИ                              ║
╠══════════════════════════════════════════════════════════════════╣""")
            for error, count in sorted(self.summary.errors.items(), key=lambda x: -x[1]):
                print(f"║  {error:<30}    {count:>10}                  ║")
        
        print("""╚══════════════════════════════════════════════════════════════════╝""")
        
        # Сохранение результатов в JSON
        self._save_results(total_time)
    
    def _get_status_name(self, status: int) -> str:
        names = {
            0: "Connection Error",
            200: "OK",
            201: "Created",
            400: "Bad Request",
            401: "Unauthorized",
            403: "Forbidden",
            404: "Not Found",
            409: "Conflict",
            422: "Unprocessable",
            429: "Too Many Requests",
            500: "Server Error",
            502: "Bad Gateway",
            503: "Unavailable",
            504: "Gateway Timeout"
        }
        return names.get(status, f"Status {status}")
    
    def _save_results(self, total_time: float):
        results = {
            "config": {
                "base_url": self.config.base_url,
                "total_clients": self.config.total_clients,
                "concurrent_clients": self.config.concurrent_clients
            },
            "summary": {
                "total_time_seconds": total_time,
                "total_requests": self.summary.total_requests,
                "successful_requests": self.summary.successful_requests,
                "failed_requests": self.summary.failed_requests,
                "success_rate_percent": self.summary.success_rate,
                "requests_per_second": self.summary.total_requests / total_time,
                "avg_duration_ms": self.summary.avg_duration_ms,
                "min_duration_ms": self.summary.min_duration_ms if self.summary.min_duration_ms != float('inf') else 0,
                "max_duration_ms": self.summary.max_duration_ms,
                "p50_ms": self.summary.p50_ms,
                "p95_ms": self.summary.p95_ms,
                "p99_ms": self.summary.p99_ms
            },
            "status_codes": dict(self.summary.status_codes),
            "errors": dict(self.summary.errors)
        }
        
        filename = f"load_test_results_{int(time.time())}.json"
        with open(filename, 'w') as f:
            json.dump(results, f, indent=2)
        print(f"\n📁 Результаты сохранены в: {filename}")

# ===== Entry Point =====

def main():
    parser = argparse.ArgumentParser(description="RMP App Load Tester — 10,000 Clients Simulator")
    parser.add_argument("--url", default="http://localhost:8080", help="Base URL of the API")
    parser.add_argument("--clients", type=int, default=10000, help="Total number of clients to simulate")
    parser.add_argument("--concurrent", type=int, default=500, help="Number of concurrent connections")
    parser.add_argument("--ramp-up", type=int, default=30, help="Ramp-up time in seconds")
    parser.add_argument("--timeout", type=int, default=30, help="Request timeout in seconds")
    
    args = parser.parse_args()
    
    config = TestConfig(
        base_url=args.url,
        total_clients=args.clients,
        concurrent_clients=args.concurrent,
        ramp_up_seconds=args.ramp_up,
        timeout_seconds=args.timeout
    )
    
    tester = LoadTester(config)
    asyncio.run(tester.run())

if __name__ == "__main__":
    main()
