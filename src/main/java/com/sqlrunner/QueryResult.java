package com.sqlrunner;

import java.util.List;
import java.util.Map;

public record QueryResult(
    List<Map<String, Object>> rows,
    int rowCount,
    List<String> columns
) {}
