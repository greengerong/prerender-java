package com.github.avaliani.snapshot;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class SnapshotResult {
    private final String snapshot;
    private final Map<String, List<String>> responseHeaders;
}