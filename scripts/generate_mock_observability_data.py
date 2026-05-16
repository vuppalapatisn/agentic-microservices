import argparse
import datetime as dt
import http.server
import json
import pathlib
import socketserver
import time
import uuid
from typing import Dict, List


ROOT = pathlib.Path(__file__).resolve().parent.parent
METRICS_DIR = ROOT / "generated-metrics"
LOGS_DIR = ROOT / "generated-logs"
STATE_FILE = METRICS_DIR / "manifest.json"
SCENARIO_DATE = dt.date(2026, 5, 13)
SERVICES = ["ecommerce", "product", "images", "observability-agent"]
GC_LABELS = 'action="end of minor GC",cause="G1 Evacuation Pause"'


def minute_range() -> List[dt.datetime]:
    start = dt.datetime(2026, 5, 13, 6, 0, tzinfo=dt.timezone.utc)
    end = dt.datetime(2026, 5, 13, 23, 30, tzinfo=dt.timezone.utc)
    current = start
    points = []
    while current <= end:
        points.append(current)
        current += dt.timedelta(minutes=1)
    return points


def reset_output_dirs() -> None:
    for directory in (METRICS_DIR, LOGS_DIR):
        directory.mkdir(parents=True, exist_ok=True)
    if STATE_FILE.exists():
        STATE_FILE.unlink(missing_ok=True)
    for service in SERVICES:
        service_dir = METRICS_DIR / service
        service_dir.mkdir(parents=True, exist_ok=True)
        for path in service_dir.glob("*.prom"):
            path.unlink(missing_ok=True)
    for path in LOGS_DIR.glob("*.log"):
        path.unlink(missing_ok=True)


def normal_load(minute_of_day: int) -> float:
    if minute_of_day < 480:
        return 7
    if minute_of_day < 720:
        return 14
    if minute_of_day < 1020:
        return 22
    if minute_of_day < 1260:
        return 16
    if minute_of_day < 1380:
        return 10
    if minute_of_day <= 1410:
        return 65
    return 5


def service_multiplier(service: str) -> float:
    return {
        "ecommerce": 1.0,
        "product": 0.96,
        "images": 0.92,
        "observability-agent": 0.10,
    }[service]


def build_series() -> Dict[str, List[dict]]:
    points = minute_range()
    state = {
        "ecommerce": {"heap_mb": 340.0, "gc_cycle": 0, "threads": 32},
        "product": {"heap_mb": 220.0, "gc_cycle": 0, "threads": 22},
        "images": {"heap_mb": 180.0, "gc_cycle": 0, "threads": 20},
        "observability-agent": {"heap_mb": 140.0, "gc_cycle": 0, "threads": 14},
    }
    max_heap_mb = {
        "ecommerce": 820.0,
        "product": 540.0,
        "images": 420.0,
        "observability-agent": 300.0,
    }
    series = {service: [] for service in SERVICES}
    gc_events = {s: 0 for s in SERVICES}
    gc_sum = {s: 0.0 for s in SERVICES}
    http_total = {s: 0 for s in SERVICES}

    for timestamp in points:
        minute_of_day = timestamp.hour * 60 + timestamp.minute
        batch_window = 1380 <= minute_of_day <= 1410
        for service in SERVICES:
            base_load = normal_load(minute_of_day) * service_multiplier(service)
            if service == "observability-agent" and batch_window:
                base_load = 12
            jitter = ((minute_of_day + len(service)) % 5) - 2
            request_rate = max(1.0, base_load + jitter)
            if service == "observability-agent":
                request_rate = max(0.5, base_load + 0.2 * jitter)

            thread_base = {
                "ecommerce": 24,
                "product": 18,
                "images": 16,
                "observability-agent": 10,
            }[service]
            thread_boost = 14 if batch_window and service != "observability-agent" else 4 if batch_window else 0
            threads = max(thread_base, int(round(thread_base + request_rate * 1.3 + thread_boost)))

            state[service]["gc_cycle"] += 1
            gc_interval = 85 if service == "ecommerce" else 105 if service == "product" else 115 if service == "images" else 140
            if batch_window and service != "observability-agent":
                gc_interval = max(9, gc_interval // 8)

            full_gc = state[service]["gc_cycle"] >= gc_interval
            gc_pause = 0.0
            if full_gc:
                gc_pause = round(1.4 + (request_rate / 18.0) + (0.3 if service == "ecommerce" else 0.1), 3)
                state[service]["heap_mb"] = max_heap_mb[service] * (0.32 if service == "ecommerce" else 0.36)
                state[service]["gc_cycle"] = 0
                gc_events[service] += 1
                gc_sum[service] += gc_pause
            else:
                growth = request_rate * (3.8 if service == "ecommerce" else 2.6 if service == "product" else 2.1 if service == "images" else 1.2)
                state[service]["heap_mb"] = min(max_heap_mb[service] * 0.92, state[service]["heap_mb"] + growth)

            http_total[service] += max(1, int(round(request_rate)))

            latency = round(180 + request_rate * (12 if service == "ecommerce" else 8), 2)
            if gc_pause > 0:
                latency += gc_pause * 1100
            if batch_window and service != "observability-agent":
                latency += 550

            heap_bytes = round(state[service]["heap_mb"] * 1024 * 1024, 3)
            max_heap_bytes = round(max_heap_mb[service] * 1024 * 1024, 3)

            series[service].append(
                {
                    "timestamp": timestamp.isoformat().replace("+00:00", "Z"),
                    "heap_bytes": heap_bytes,
                    "max_heap_bytes": max_heap_bytes,
                    "jvm_threads_live_threads": float(threads),
                    "http_server_requests_seconds_count": float(http_total[service]),
                    "jvm_gc_pause_seconds_count": float(gc_events[service]),
                    "jvm_gc_pause_seconds_sum": round(gc_sum[service], 6),
                    "jvm_gc_pause_seconds_max": round(gc_pause, 6) if gc_pause > 0 else 0.0,
                    "full_gc": full_gc,
                    "http_request_duration_p95": latency,
                }
            )

    return series


def write_metric_files(series: Dict[str, List[dict]]) -> None:
    for service, points in series.items():
        service_dir = METRICS_DIR / service
        for point in points:
            file_name = point["timestamp"].replace(":", "").replace("-", "")
            path = service_dir / f"{file_name}.prom"
            lines = [
                "# HELP jvm_memory_used_bytes Used heap memory",
                "# TYPE jvm_memory_used_bytes gauge",
                f'jvm_memory_used_bytes{{area="heap",id="mock"}} {point["heap_bytes"]}',
                "# HELP jvm_memory_max_bytes Max heap memory",
                "# TYPE jvm_memory_max_bytes gauge",
                f'jvm_memory_max_bytes{{area="heap",id="mock"}} {point["max_heap_bytes"]}',
                "# HELP jvm_threads_live_threads Live thread count",
                "# TYPE jvm_threads_live_threads gauge",
                f'jvm_threads_live_threads {point["jvm_threads_live_threads"]}',
                "# HELP jvm_gc_pause_seconds_count GC pause count",
                "# TYPE jvm_gc_pause_seconds_count counter",
                f"jvm_gc_pause_seconds_count{{{GC_LABELS}}} {point['jvm_gc_pause_seconds_count']}",
                "# HELP jvm_gc_pause_seconds_sum GC pause sum",
                "# TYPE jvm_gc_pause_seconds_sum counter",
                f"jvm_gc_pause_seconds_sum{{{GC_LABELS}}} {point['jvm_gc_pause_seconds_sum']}",
                "# HELP jvm_gc_pause_seconds_max GC pause max",
                "# TYPE jvm_gc_pause_seconds_max gauge",
                f"jvm_gc_pause_seconds_max{{{GC_LABELS}}} {point['jvm_gc_pause_seconds_max']}",
                "# HELP http_server_requests_seconds_count HTTP request count",
                "# TYPE http_server_requests_seconds_count counter",
                f'http_server_requests_seconds_count{{uri="/",method="GET",status="200"}} {point["http_server_requests_seconds_count"]}',
            ]
            path.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")

    manifest = {
        "scenarioDate": SCENARIO_DATE.isoformat(),
        "startTimestamp": series["ecommerce"][0]["timestamp"],
        "services": {
            service: [point["timestamp"].replace(":", "").replace("-", "") + ".prom" for point in points]
            for service, points in series.items()
        },
    }
    STATE_FILE.write_text(json.dumps(manifest, indent=2), encoding="utf-8")


def log_json(
    timestamp: dt.datetime,
    level: str,
    correlation_id: str,
    service: str,
    thread: str,
    logger: str,
    message: str,
) -> str:
    return json.dumps(
        {
            "timestamp": timestamp.strftime("%Y-%m-%dT%H:%M:%SZ"),
            "service": service,
            "level": level,
            "correlationId": correlation_id,
            "thread": thread,
            "logger": logger,
            "message": message,
        },
        ensure_ascii=False,
    )


def write_log_files(series: Dict[str, List[dict]]) -> None:
    logs = {service: [] for service in SERVICES}
    for idx, point in enumerate(series["ecommerce"]):
        timestamp = dt.datetime.strptime(point["timestamp"], "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=dt.timezone.utc)
        correlation_id = str(uuid.uuid4())

        ecommerce_duration = int(point["http_request_duration_p95"])
        product_duration = max(120, int(ecommerce_duration * 0.34))
        images_duration = max(95, int(ecommerce_duration * 0.28))

        logs["ecommerce"].append(
            log_json(timestamp, "INFO", correlation_id, "ecommerce", "http-nio-8090-exec-1", "mock", "Request received")
        )
        logs["product"].append(
            log_json(
                timestamp + dt.timedelta(milliseconds=25),
                "INFO",
                correlation_id,
                "product",
                "http-nio-8090-exec-2",
                "mock",
                "Downstream request completed",
            )
        )
        logs["images"].append(
            log_json(
                timestamp + dt.timedelta(milliseconds=45),
                "INFO",
                correlation_id,
                "images",
                "http-nio-8090-exec-3",
                "mock",
                "Downstream request completed",
            )
        )

        if point["full_gc"]:
            gc_duration = max(5200, ecommerce_duration)
            logs["ecommerce"].append(
                log_json(
                    timestamp + dt.timedelta(milliseconds=20),
                    "WARN",
                    correlation_id,
                    "ecommerce",
                    "http-nio-8090-exec-1",
                    "mock",
                    "Full GC pause detected",
                )
            )
            logs["product"].append(
                log_json(
                    timestamp + dt.timedelta(milliseconds=30),
                    "WARN",
                    correlation_id,
                    "product",
                    "http-nio-8090-exec-2",
                    "mock",
                    "Slow request during upstream GC pressure",
                )
            )
            logs["images"].append(
                log_json(
                    timestamp + dt.timedelta(milliseconds=35),
                    "WARN",
                    correlation_id,
                    "images",
                    "http-nio-8090-exec-3",
                    "mock",
                    "Slow request during upstream GC pressure",
                )
            )

        logs["ecommerce"].append(
            log_json(
                timestamp + dt.timedelta(milliseconds=ecommerce_duration),
                "INFO" if ecommerce_duration < 5000 else "WARN",
                correlation_id,
                "ecommerce",
                "http-nio-8090-exec-1",
                "mock",
                "Request completed",
            )
        )

        if idx % 12 == 0:
            agent_duration = 70 + (idx % 6) * 15
            logs["observability-agent"].append(
                log_json(
                    timestamp + dt.timedelta(seconds=4),
                    "INFO",
                    correlation_id,
                    "observability-agent",
                    "reactor-http-nio-2",
                    "mock",
                    "Observability query completed",
                )
            )

    for service, entries in logs.items():
        (LOGS_DIR / f"{service}.log").write_text("\n".join(entries) + "\n", encoding="utf-8", newline="\n")


class MetricHandler(http.server.BaseHTTPRequestHandler):
    manifest = {}
    start_time = time.time()
    seconds_per_minute = 1.0

    def do_GET(self):
        service = self.path.strip("/").replace(".prom", "")
        if service not in self.manifest["services"]:
            self.send_response(404)
            self.end_headers()
            return

        elapsed = max(0.0, time.time() - self.start_time)
        index = min(int(elapsed / self.seconds_per_minute), len(self.manifest["services"][service]) - 1)
        file_name = self.manifest["services"][service][index]
        content = (METRICS_DIR / service / file_name).read_text(encoding="utf-8")

        self.send_response(200)
        self.send_header("Content-Type", "text/plain; version=0.0.4; charset=utf-8")
        self.end_headers()
        self.wfile.write(content.encode("utf-8"))

    def log_message(self, format, *args):
        return


def serve_metrics(port: int, seconds_per_minute: float) -> None:
    manifest = json.loads(STATE_FILE.read_text(encoding="utf-8"))
    MetricHandler.manifest = manifest
    MetricHandler.start_time = time.time()
    MetricHandler.seconds_per_minute = seconds_per_minute
    with socketserver.TCPServer(("", port), MetricHandler) as server:
        server.serve_forever()


def generate() -> None:
    reset_output_dirs()
    series = build_series()
    write_metric_files(series)
    write_log_files(series)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["generate", "serve"])
    parser.add_argument("--port", type=int, default=9105)
    parser.add_argument("--seconds-per-minute", type=float, default=1.0)
    args = parser.parse_args()

    if args.command == "generate":
        generate()
        print(f"Generated metrics in {METRICS_DIR}")
        print(f"Generated logs in {LOGS_DIR}")
        return

    serve_metrics(args.port, args.seconds_per_minute)


if __name__ == "__main__":
    main()
