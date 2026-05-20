from statistics import mean

from app.models.schemas import CorrelationFinding, InvestigationContext, LogFinding, MetricFinding


class CorrelationEngine:
    def correlate(self, context: InvestigationContext) -> CorrelationFinding:
        evidence: list[str] = []
        tags: list[str] = []
        scores = {
            "resource saturation": 0,
            "traffic overload": 0,
            "downstream dependency issue": 0,
            "request-specific failure": 0,
            "insufficient telemetry": 0,
        }

        error_count = len(context.error_logs)
        timeout_logs = [log for log in context.logs + context.error_logs if "timeout" in log.message.lower()]
        slow_logs = [log for log in context.logs + context.error_logs if log.duration_ms and log.duration_ms >= 5000]

        request_rate_peak = self._peak(context.request_rate_metrics)
        request_rate_avg = self._average(context.request_rate_metrics)
        heap_peak = self._peak(context.heap_metrics)
        heap_avg = self._average(context.heap_metrics)
        thread_peak = self._peak(context.thread_metrics)
        thread_avg = self._average(context.thread_metrics)

        if error_count:
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
                f"Request rate spiked from an average of {request_rate_avg:.2f} rps to {request_rate_peak:.2f} rps."
            )
            tags.append("request-rate-spike")
            scores["traffic overload"] += 2

        if heap_peak and heap_avg and heap_peak > heap_avg * 1.5:
            evidence.append(
                f"Heap usage rose from an average of {heap_avg:.2f} to a peak of {heap_peak:.2f}."
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
                f"Request id {context.request_id} appears across multiple services, which indicates a cross-service flow."
            )
            scores["request-specific failure"] += 2
            if timeout_logs or error_count:
                scores["downstream dependency issue"] += 2

        if not evidence:
            evidence.append("No strong anomaly correlation was found from the available telemetry.")
            scores["insufficient telemetry"] += 1

        probable_root_cause = max(scores, key=scores.get)
        return CorrelationFinding(probable_root_cause=probable_root_cause, evidence=evidence, tags=tags)

    @staticmethod
    def _peak(metrics: list[MetricFinding]) -> float | None:
        return max((metric.value for metric in metrics), default=None)

    @staticmethod
    def _average(metrics: list[MetricFinding]) -> float | None:
        values = [metric.value for metric in metrics]
        return mean(values) if values else None
