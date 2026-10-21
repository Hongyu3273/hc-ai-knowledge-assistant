package com.hc.aiagenthyc.app.model;

import java.util.List;

/**
 * Structured report containing relationship advice.
 *
 * @param title report title
 * @param suggestions suggested actions
 */
public record LoveReport(String title, List<String> suggestions) {
}
