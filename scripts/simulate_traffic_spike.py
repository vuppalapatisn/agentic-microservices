#!/usr/bin/env python3
"""
Simulate a sudden traffic spike against the ecommerce service for observability demos.

Traffic shape (request rate over time):
    Phase 1 — warmup:  5 req/s  for 30s
    Phase 2 — spike:    400 req/s for 180s (configurable 300–500)
    Phase 3 — stop:     abrupt (no ramp-down)

Creates visible Prometheus/Grafana signals:
    - http_server_requests_seconds_count rate spike
    - jvm_memory_used_bytes (heap) increase
    - slow requests and client-side timeouts in logs

Correlate failures in Loki (after spike):
    {namespace="ecommerce", app="ecommerce"} |= "<correlation-id-from-script-log>"

Example:
    pip install -r scripts/requirements.txt
    python scripts/simulate_traffic_spike.py
    python scripts/simulate_traffic_spike.py --spike-rps 500 --spike-duration 300
"""

from __future__ import annotations

import argparse
import asyncio
import random
import signal
import sys
import time
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Optional

import aiohttp


def utc_timestamp() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z"


def log_event(
    *,
    correlation_id: str,
    status: str,
    latency_ms: float,
    error: str = "",
    user_id: str = "",
) -> None:
    """Structured single-line log for local correlation with Loki/Grafana."""
    line = (
        f"timestamp={utc_timestamp()} "
        f"correlationId={correlation_id} "
        f"userId={user_id} "
        f"status={status} "
        f"latencyMs={latency_ms:.0f}"
    )
    if error:
        line += f" error={error}"
    print(line, flush=True)


@dataclass
class PhaseConfig:
    name: str
    rps: int
    duration_sec: int


@dataclass
class RunConfig:
    url: str
    warmup: PhaseConfig
    spike: PhaseConfig
    concurrency: int
    connect_timeout_sec: float
    sock_read_timeout_sec: float
    total_timeout_sec: float
    stats_interval_sec: int
    product_id_min: int
    product_id_max: int


class StatsCollector:
    """Thread-free stats for asyncio load test (protected by asyncio.Lock)."""

    def __init__(self) -> None:
        self._lock = asyncio.Lock()
        self._latencies_ms: list[float] = []
        self.success = 0
        self.errors = 0
        self.timeouts = 0
        self.completed = 0
        self._window_started = time.monotonic()
        self._window_completed = 0

    async def record_success(self, latency_ms: float) -> None:
        async with self._lock:
            self.success += 1
            self.completed += 1
            self._window_completed += 1
            self._latencies_ms.append(latency_ms)

    async def record_error(self, latency_ms: float, is_timeout: bool) -> None:
        async with self._lock:
            self.errors += 1
            self.completed += 1
            self._window_completed += 1
            if is_timeout:
                self.timeouts += 1
            self._latencies_ms.append(latency_ms)

    async def snapshot_and_reset_window(self) -> dict:
        async with self._lock:
            elapsed = max(time.monotonic() - self._window_started, 0.001)
            rps = self._window_completed / elapsed
            latencies = self._latencies_ms
            self._latencies_ms = []
            self._window_started = time.monotonic()
            self._window_completed = 0

        if not latencies:
            return {
                "rps": rps,
                "avg_ms": 0.0,
                "p95_ms": 0.0,
                "success": self.success,
                "errors": self.errors,
                "timeouts": self.timeouts,
            }

        sorted_lat = sorted(latencies)
        p95_index = min(int(len(sorted_lat) * 0.95), len(sorted_lat) - 1)
        return {
            "rps": rps,
            "avg_ms": sum(latencies) / len(latencies),
            "p95_ms": sorted_lat[p95_index],
            "success": self.success,
            "errors": self.errors,
            "timeouts": self.timeouts,
        }


class TrafficSpikeSimulator:
    def __init__(self, config: RunConfig) -> None:
        self.config = config
        self.stats = StatsCollector()
        self._stop = asyncio.Event()
        self._inflight: set[asyncio.Task] = set()
        self._semaphore: Optional[asyncio.Semaphore] = None
        self._session: Optional[aiohttp.ClientSession] = None

    def request_stop(self) -> None:
        self._stop.set()

    def _build_headers(self) -> tuple[dict[str, str], str, str]:
        correlation_id = str(uuid.uuid4())
        user_id = str(uuid.uuid4())
        product_id = str(random.randint(self.config.product_id_min, self.config.product_id_max))
        headers = {
            "X-Request-ID": str(uuid.uuid4()),
            "X-Correlation-Id": correlation_id,
            "X-User-ID": user_id,
            "X-Product-ID": product_id,
            "Accept": "application/json",
        }
        return headers, correlation_id, user_id

    async def _one_request(self) -> None:
        assert self._session is not None
        assert self._semaphore is not None

        headers, correlation_id, user_id = self._build_headers()
        start = time.perf_counter()
        status_code = "0"
        error = ""

        async with self._semaphore:
            if self._stop.is_set():
                return
            try:
                async with self._session.get(self.config.url, headers=headers) as response:
                    await response.read()
                    status_code = str(response.status)
                    latency_ms = (time.perf_counter() - start) * 1000
                    if response.status >= 400:
                        error = f"http_{response.status}"
                        log_event(
                            correlation_id=correlation_id,
                            status=status_code,
                            latency_ms=latency_ms,
                            error=error,
                            user_id=user_id,
                        )
                        await self.stats.record_error(latency_ms, is_timeout=False)
                    else:
                        log_event(
                            correlation_id=correlation_id,
                            status=status_code,
                            latency_ms=latency_ms,
                            user_id=user_id,
                        )
                        await self.stats.record_success(latency_ms)
            except asyncio.TimeoutError:
                latency_ms = (time.perf_counter() - start) * 1000
                error = "timeout"
                log_event(
                    correlation_id=correlation_id,
                    status="timeout",
                    latency_ms=latency_ms,
                    error=error,
                    user_id=user_id,
                )
                await self.stats.record_error(latency_ms, is_timeout=True)
            except aiohttp.ClientError as exc:
                latency_ms = (time.perf_counter() - start) * 1000
                error = type(exc).__name__
                log_event(
                    correlation_id=correlation_id,
                    status="error",
                    latency_ms=latency_ms,
                    error=error,
                    user_id=user_id,
                )
                await self.stats.record_error(latency_ms, is_timeout=False)
            except asyncio.CancelledError:
                raise
            except Exception as exc:
                latency_ms = (time.perf_counter() - start) * 1000
                error = type(exc).__name__
                log_event(
                    correlation_id=correlation_id,
                    status="error",
                    latency_ms=latency_ms,
                    error=error,
                    user_id=user_id,
                )
                await self.stats.record_error(latency_ms, is_timeout=False)

    def _track_task(self, task: asyncio.Task) -> None:
        self._inflight.add(task)

        def _done(t: asyncio.Task) -> None:
            self._inflight.discard(t)
            if not t.cancelled() and t.exception():
                pass  # errors recorded per request

        task.add_done_callback(_done)

    async def _dispatch_at_rps(self, rps: int) -> None:
        """Release exactly `rps` request tasks per second with no gradual ramp."""
        interval = 1.0 / rps if rps > 0 else 1.0
        for _ in range(rps):
            if self._stop.is_set():
                return
            task = asyncio.create_task(self._one_request())
            self._track_task(task)
            if interval > 0:
                await asyncio.sleep(interval)

    async def _run_phase(self, phase: PhaseConfig) -> None:
        print(
            f"phase_start name={phase.name} rps={phase.rps} durationSec={phase.duration_sec}",
            flush=True,
        )
        end_at = time.monotonic() + phase.duration_sec
        while time.monotonic() < end_at and not self._stop.is_set():
            await self._dispatch_at_rps(phase.rps)
        print(f"phase_end name={phase.name}", flush=True)

    async def _stats_loop(self) -> None:
        while not self._stop.is_set():
            await asyncio.sleep(self.config.stats_interval_sec)
            snap = await self.stats.snapshot_and_reset_window()
            print(
                "stats "
                f"rps={snap['rps']:.1f} "
                f"avgLatencyMs={snap['avg_ms']:.0f} "
                f"p95LatencyMs={snap['p95_ms']:.0f} "
                f"timeouts={snap['timeouts']} "
                f"success={snap['success']} "
                f"errors={snap['errors']}",
                flush=True,
            )

    async def run(self) -> None:
        timeout = aiohttp.ClientTimeout(
            total=self.config.total_timeout_sec,
            connect=self.config.connect_timeout_sec,
            sock_read=self.config.sock_read_timeout_sec,
        )
        connector = aiohttp.TCPConnector(
            limit=self.config.concurrency,
            limit_per_host=self.config.concurrency,
            ttl_dns_cache=300,
        )

        self._semaphore = asyncio.Semaphore(self.config.concurrency)
        self._session = aiohttp.ClientSession(timeout=timeout, connector=connector)
        stats_task = asyncio.create_task(self._stats_loop())

        try:
            await self._run_phase(self.config.warmup)
            if not self._stop.is_set():
                await self._run_phase(self.config.spike)
            print("phase_end name=stop traffic=0", flush=True)
        finally:
            self._stop.set()
            stats_task.cancel()
            try:
                await stats_task
            except asyncio.CancelledError:
                pass

            for task in list(self._inflight):
                task.cancel()
            if self._inflight:
                await asyncio.gather(*self._inflight, return_exceptions=True)

            if self._session:
                await self._session.close()
                await asyncio.sleep(0.25)

        snap = await self.stats.snapshot_and_reset_window()
        print(
            "summary "
            f"completed={self.stats.completed} "
            f"success={snap['success']} "
            f"errors={snap['errors']} "
            f"timeouts={snap['timeouts']}",
            flush=True,
        )


def parse_args(argv: list[str]) -> RunConfig:
    parser = argparse.ArgumentParser(
        description="Simulate sudden ecommerce traffic spike for Grafana/Loki demos.",
    )
    parser.add_argument(
        "--url",
        default="http://localhost:8090/ecommerce-service/ecommerceProducts",
        help="Target ecommerce API endpoint",
    )
    parser.add_argument("--warmup-rps", type=int, default=5, help="Phase 1 requests per second")
    parser.add_argument("--warmup-duration", type=int, default=30, help="Phase 1 duration (seconds)")
    parser.add_argument("--spike-rps", type=int, default=400, help="Phase 2 requests per second (300-500)")
    parser.add_argument("--spike-duration", type=int, default=180, help="Phase 2 duration (seconds)")
    parser.add_argument(
        "--concurrency",
        type=int,
        default=500,
        help="Max in-flight requests (connection pool + semaphore)",
    )
    parser.add_argument("--connect-timeout", type=float, default=1.0, help="TCP connect timeout (seconds)")
    parser.add_argument("--sock-read-timeout", type=float, default=2.0, help="Socket read timeout (seconds)")
    parser.add_argument("--total-timeout", type=float, default=3.0, help="Total request timeout (seconds)")
    parser.add_argument("--stats-interval", type=int, default=5, help="Stats print interval (seconds)")
    parser.add_argument("--product-id-min", type=int, default=1)
    parser.add_argument("--product-id-max", type=int, default=500)
    args = parser.parse_args(argv)

    if args.spike_rps < 300 or args.spike_rps > 500:
        print(
            f"warning: spike-rps={args.spike_rps} outside recommended 300-500 range",
            file=sys.stderr,
        )

    return RunConfig(
        url=args.url,
        warmup=PhaseConfig("warmup", args.warmup_rps, args.warmup_duration),
        spike=PhaseConfig("spike", args.spike_rps, args.spike_duration),
        concurrency=args.concurrency,
        connect_timeout_sec=args.connect_timeout,
        sock_read_timeout_sec=args.sock_read_timeout,
        total_timeout_sec=args.total_timeout,
        stats_interval_sec=args.stats_interval,
        product_id_min=args.product_id_min,
        product_id_max=args.product_id_max,
    )


def main(argv: list[str] | None = None) -> int:
    config = parse_args(argv or sys.argv[1:])
    simulator = TrafficSpikeSimulator(config)
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)

    for sig in (signal.SIGINT, signal.SIGTERM):
        try:
            loop.add_signal_handler(sig, simulator.request_stop)
        except NotImplementedError:
            # Windows limited signal support in some environments
            signal.signal(sig, lambda _s, _f: simulator.request_stop())

    print(
        f"run_start url={config.url} "
        f"warmup={config.warmup.rps}rps/{config.warmup.duration_sec}s "
        f"spike={config.spike.rps}rps/{config.spike.duration_sec}s "
        f"concurrency={config.concurrency}",
        flush=True,
    )

    try:
        loop.run_until_complete(simulator.run())
    finally:
        loop.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
