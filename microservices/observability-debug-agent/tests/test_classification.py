from app.graph.classification import classify_investigation


def test_latency_query_fetches_percentiles():
    result = classify_investigation("why is the ecommerce service so slow?")
    assert result["needs_monitoring"] is True
    assert result["fetch_latency_percentiles"] is True


def test_heap_percent_query_skips_latency():
    result = classify_investigation("what is the current heap usage percent for product?")
    assert result["heap_usage_percent_query"] is True
    assert result["fetch_latency_percentiles"] is False


def test_pure_error_log_query_skips_latency():
    result = classify_investigation("show me the error logs for the coupon endpoint")
    assert result["needs_monitoring"] is False
    assert result["fetch_latency_percentiles"] is False
