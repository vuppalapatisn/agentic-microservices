from statistics import mean

from app.models.schemas import CorrelationFinding, InvestigationContext, LogFinding, MetricFinding
from app.util.formatting import format_bytes, format_percent, format_rps


class CorrelationEngine:
    def correlate(self, context: InvestigationContext) -> CorrelationFinding:
        if context.heap_usage_percent_query:
            return self._correlate_heap_percent(context)

        evidence: list[str] = []
        tags: list[str] = []
        scores = {
            "resource saturation": 0,
            "traffic overload": 0,
            "downstream dependency issue": 0,
            "request-specific failure": 0,
            "insufficient telemetry": 0,
        }

        metrics_fetched = bool(
            context.heap_metrics or context.thread_metrics or context.request_rate_metrics
        )
        if not metrics_fetched:
            self._add_log_error_evidence(context, evidence, scores)

        error_count = len(context.error_logs)
        timeout_logs = [log for log in context.logs + context.error_logs if "timeout" in log.message.lower()]
        slow_logs = [log for log in context.logs + context.error_logs if log.duration_ms and log.duration_ms >= 5000]

        request_rate_peak = self._peak(context.request_rate_metrics)
        request_rate_avg = self._average(context.request_rate_metrics)
        heap_peak = self._peak(context.heap_metrics)
        heap_avg = self._average(context.heap_metrics)
        thread_peak = self._peak(context.thread_metrics)
        thread_avg = self._average(context.thread_metrics)

        if error_count and metrics_fetched:
            evidence.append(f"{error_count} error log entries were found for {context.service_name}.")
            scores["traffic overload"] += 1

        if timeout_logs:
            evidence.append(f"{len(timeout_logs)} logs mention timeout behavior.")
            scores["traffic overload"] += 1

        if slow_logs:
            evidence.append(f"{len(slow_logs)} requests took 5000 ms or more.")
            scores["resource saturation"] += 1

        if request_rate_peak and request_rate_avg and request_rate_peak > request_rate_avg * 1.8:
            evidence.append(
                f"Request rate spiked from an average of {format_rps(request_rate_avg)} to a peak of {format_rps(request_rate_peak)}."
            )
            tags.append("request-rate-spike")
            scores["traffic overload"] += 2

        if heap_peak and heap_avg and heap_peak > heap_avg * 1.5:
            evidence.append(
                f"Heap usage rose from an average of {format_bytes(heap_avg)} to a peak of {format_bytes(heap_peak)}."
            )
            tags.append("heap-spike")
            scores["resource saturation"] += 2

        if thread_peak and thread_avg and thread_peak > max(thread_avg * 1.5, thread_avg + 20):
            evidence.append(
                f"Thread count rose from an average of {thread_avg:.2f} to a peak of {thread_peak:.2f}."
            )
            tags.append("thread-spike")
            scores["resource saturation"] += 2

        if "heap-spike" in tags and "thread-spike" in tags and (timeout_logs or slow_logs):
            evidence.append("Heap growth, thread growth, and slow requests line up with JVM pressure.")
            scores["resource saturation"] += 2

        if context.request_id and len({log.service for log in context.logs}) > 1:
            evidence.append(
                f"Correlation id {context.request_id} appears across multiple services, which indicates a cross-service flow."
            )
            scores["request-specific failure"] += 2
            if timeout_logs or error_count:
                scores["downstream dependency issue"] += 2

        if not evidence:
            evidence.append("No strong anomaly correlation was found from the available telemetry.")
            scores["insufficient telemetry"] += 1

        probable_root_cause = max(scores, key=scores.get)
        return CorrelationFinding(probable_root_cause=probable_root_cause, evidence=evidence, tags=tags)

    def _correlate_heap_percent(self, context: InvestigationContext) -> CorrelationFinding:
        used = self._latest(context.heap_metrics)
        max_heap = self._latest(context.heap_max_metrics)
        evidence: list[str] = []
        if used is not None and max_heap is not None and max_heap > 0:
            percent = (used / max_heap) * 100
            evidence.append(
                f"Heap usage is {format_percent(percent)} ({format_bytes(used)} of {format_bytes(max_heap)})."
            )
        elif used is not None:
            evidence.append(f"Heap used is {format_bytes(used)}; max heap is unavailable.")
        else:
            evidence.append("Heap metrics are unavailable for the selected time window.")
        return CorrelationFinding(
            probable_root_cause="heap usage report",
            evidence=evidence,
            tags=["heap-usage-percent"],
        )

    @staticmethod
    def _add_log_error_evidence(
        context: InvestigationContext, evidence: list[str], scores: dict[str, int]
    ) -> None:
        error_logs = [
            log
            for log in context.logs + context.error_logs
            if log.level.upper() in {"ERROR", "WARN"}
        ]
        if error_logs:
            excerpt = error_logs[0].message[:300]
            evidence.append(f"Log excerpt: {excerpt}")
            scores["downstream dependency issue"] += 2
        if any("coupon" in log.message.lower() for log in error_logs):
            scores["downstream dependency issue"] += 1

    @staticmethod
    def _peak(metrics: list[MetricFinding]) -> float | None:
        return max((metric.value for metric in metrics), default=None)

    @staticmethod
    def _latest(metrics: list[MetricFinding]) -> float | None:
        if not metrics:
            return None
        latest = max(metrics, key=lambda metric: metric.timestamp)
        return latest.value

    @staticmethod
    def _average(metrics: list[MetricFinding]) -> float | None:
        values = [metric.value for metric in metrics]
        return mean(values) if values else None
