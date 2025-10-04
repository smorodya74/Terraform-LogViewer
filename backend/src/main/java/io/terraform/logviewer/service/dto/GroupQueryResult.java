package io.terraform.logviewer.service.dto;

import java.util.List;

public record GroupQueryResult(long totalGroups, List<LogGroupResult> groups) {
}
