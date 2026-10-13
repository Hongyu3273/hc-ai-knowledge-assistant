package com.hc.aiagenthyc.app.model;

import java.util.List;

/**
 * 恋爱咨询的结构化报告。
 *
 * @param title 报告标题
 * @param suggestions 建议列表
 */
public record LoveReport(String title, List<String> suggestions) {
}
