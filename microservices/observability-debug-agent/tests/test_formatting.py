from app.util.formatting import format_bytes, format_count


def test_format_bytes_uses_mb_and_gb():
    assert format_bytes(26_353_617) == "25.13 MB"
    assert format_bytes(65_274_256) == "62.25 MB"
    assert format_bytes(2 * 1024**3) == "2.00 GB"


def test_format_count_rounds_to_whole_number():
    assert format_count(69.43) == "69"
    assert format_count(213.0) == "213"
