package com.tonyghouse.socialraven.service.reporting;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.tonyghouse.socialraven.dto.reporting.ClientReportCampaignInsightResponse;
import com.tonyghouse.socialraven.dto.reporting.ClientReportContributionResponse;
import com.tonyghouse.socialraven.dto.reporting.ClientReportContributionRowResponse;
import com.tonyghouse.socialraven.dto.reporting.ClientReportForecastItemResponse;
import com.tonyghouse.socialraven.dto.reporting.ClientReportPlatformPerformanceResponse;
import com.tonyghouse.socialraven.dto.reporting.ClientReportSummaryResponse;
import com.tonyghouse.socialraven.dto.reporting.ClientReportTopPostResponse;
import com.tonyghouse.socialraven.dto.reporting.PublicClientReportResponse;
import com.tonyghouse.socialraven.exception.SocialRavenException;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

@Service
public class ClientReportPdfService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("MMM d, yyyy 'at' HH:mm 'UTC'", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    public byte[] render(PublicClientReportResponse report) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(buildHtml(report), null);
            builder.toStream(outputStream);
            builder.run();
            return outputStream.toByteArray();
        } catch (Exception ex) {
            throw new SocialRavenException("Failed to generate client report PDF", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String buildHtml(PublicClientReportResponse report) {
        StringBuilder html = new StringBuilder();
        html.append("""
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8" />
                    <style>
                        @page { size: A4; margin: 18mm 16mm; }
                        body { font-family: Helvetica, Arial, sans-serif; color: #172b4d; font-size: 12px; line-height: 1.5; }
                        .page { width: 100%; }
                        .header { border-bottom: 2px solid #dfe1e6; padding-bottom: 18px; margin-bottom: 20px; }
                        .header-table { width: 100%; border-collapse: collapse; }
                        .header-table td { vertical-align: top; }
                        .logo-box { text-align: right; }
                        .logo-box img { max-width: 88px; max-height: 88px; }
                        .eyebrow { color: #6b778c; text-transform: uppercase; letter-spacing: 0.12em; font-size: 10px; margin: 0 0 8px; }
                        h1 { font-size: 26px; line-height: 1.2; margin: 0 0 10px; }
                        h2 { font-size: 16px; margin: 0 0 10px; }
                        h3 { font-size: 13px; margin: 0 0 8px; }
                        .muted { color: #5e6c84; }
                        .meta-row { margin-top: 8px; }
                        .meta-pill { display: inline-block; margin-right: 8px; margin-bottom: 6px; padding: 4px 8px; border: 1px solid #dfe1e6; border-radius: 999px; font-size: 10px; color: #42526e; }
                        .section { margin-bottom: 22px; }
                        .summary { background: #f7f8fa; border: 1px solid #dfe1e6; border-radius: 12px; padding: 14px 16px; }
                        .kpi-table { width: 100%; border-collapse: separate; border-spacing: 10px; margin: 0 -10px 8px; }
                        .kpi-card { width: 25%; border: 1px solid #dfe1e6; border-radius: 12px; padding: 12px; background: #ffffff; }
                        .kpi-label { font-size: 10px; color: #6b778c; text-transform: uppercase; letter-spacing: 0.08em; margin-bottom: 8px; }
                        .kpi-value { font-size: 22px; font-weight: bold; color: #0c66e4; margin-bottom: 6px; }
                        .highlights { margin: 0; padding-left: 18px; }
                        .highlights li { margin-bottom: 8px; }
                        .forecast-grid { width: 100%; border-collapse: separate; border-spacing: 10px; margin: 0 -10px 8px; }
                        .forecast-card { width: 33.33%; border: 1px solid #dfe1e6; border-radius: 12px; padding: 12px; background: #ffffff; }
                        .insight-grid { width: 100%; border-collapse: separate; border-spacing: 10px; margin: 0 -10px 8px; }
                        .insight-card { width: 50%; border: 1px solid #dfe1e6; border-radius: 12px; padding: 12px; background: #ffffff; }
                        table.data-table { width: 100%; border-collapse: collapse; margin-top: 10px; }
                        .data-table th { text-align: left; font-size: 10px; text-transform: uppercase; letter-spacing: 0.08em; color: #5e6c84; background: #f7f8fa; padding: 10px; border-bottom: 1px solid #dfe1e6; }
                        .data-table td { padding: 10px; border-bottom: 1px solid #ebecf0; vertical-align: top; }
                        .top-post { border: 1px solid #dfe1e6; border-radius: 12px; padding: 14px; margin-bottom: 12px; background: #ffffff; }
                        .footer { margin-top: 26px; padding-top: 14px; border-top: 1px solid #dfe1e6; font-size: 10px; color: #6b778c; }
                    </style>
                </head>
                <body>
                <div class="page">
                """);

        html.append("<div class=\"header\">");
        html.append("<table class=\"header-table\"><tr><td>");
        html.append("<p class=\"eyebrow\">Branded Client Report</p>");
        html.append("<h1>").append(escape(report.getReportTitle())).append("</h1>");
        html.append("<div class=\"muted\">Prepared for ")
                .append(escape(report.getClientLabel()))
                .append(" by ")
                .append(escape(report.getAgencyLabel()))
                .append("</div>");
        html.append("<div class=\"meta-row\">");
        appendMetaPill(html, report.getReportWindowLabel());
        appendMetaPill(html, prettyTemplate(report.getTemplateType()));
        appendMetaPill(html, report.getReportScopeLabel());
        if (StringUtils.hasText(report.getCampaignLabel())) {
            appendMetaPill(html, report.getCampaignLabel());
        }
        appendMetaPill(html, "Generated " + formatDateTime(report.getGeneratedAt()));
        html.append("</div>");
        html.append("</td><td class=\"logo-box\">");
        if (StringUtils.hasText(report.getLogoUrl())) {
            html.append("<img src=\"").append(escapeAttribute(report.getLogoUrl())).append("\" alt=\"Agency logo\" />");
        }
        html.append("</td></tr></table>");
        html.append("</div>");

        html.append("<div class=\"section summary\">");
        html.append("<h2>Executive Summary</h2>");
        html.append("<div>").append(escape(report.getCommentary())).append("</div>");
        html.append("</div>");

        html.append("<div class=\"section\">");
        html.append("<h2>Headline Metrics</h2>");
        html.append("<table class=\"kpi-table\"><tr>");
        ClientReportSummaryResponse summary = report.getSummary();
        appendKpiCard(
                html,
                "Impressions",
                formatNumber(summary != null ? summary.getImpressions() : 0L),
                "Engagements " + formatNumber(summary != null ? summary.getEngagements() : 0L)
        );
        appendKpiCard(
                html,
                "Engagement Rate",
                formatPercent(summary != null ? summary.getEngagementRate() : 0.0d),
                "Average across published posts in this window"
        );
        appendKpiCard(
                html,
                "Posts Published",
                formatNumber(summary != null ? summary.getPostsPublished() : 0L),
                "Clicks " + formatNumber(summary != null ? summary.getClicks() : 0L)
        );
        appendKpiCard(
                html,
                "Video Views",
                formatNumber(summary != null ? summary.getVideoViews() : 0L),
                "Scope: " + report.getReportScopeLabel()
        );
        html.append("</tr></table>");
        html.append("</div>");

        html.append("<div class=\"section\">");
        html.append("<h2>Highlights</h2>");
        html.append("<ul class=\"highlights\">");
        for (String highlight : safeList(report.getHighlights())) {
            html.append("<li>").append(escape(highlight)).append("</li>");
        }
        html.append("</ul>");
        html.append("</div>");

        html.append("<div class=\"section\">");
        html.append("<h2>Forecast Outlook</h2>");
        appendForecast(html, report);
        html.append("</div>");

        html.append("<div class=\"section\">");
        html.append("<h2>Platform Performance</h2>");
        appendPlatformTable(html, safeList(report.getPlatformPerformance()));
        html.append("</div>");

        if ("CAMPAIGN".equals(report.getReportScope())) {
            html.append("<div class=\"section\">");
            html.append("<h2>Campaign Insight</h2>");
            appendCampaignInsight(html, report.getCampaignInsight());
            html.append("</div>");
        }

        html.append("<div class=\"section\">");
        html.append("<h2>Top Content</h2>");
        appendTopPosts(html, safeList(report.getTopPosts()));
        html.append("</div>");

        html.append("<div class=\"footer\">");
        html.append("Workspace: ")
                .append(escape(report.getWorkspaceName()))
                .append(" | Company: ")
                .append(escape(report.getCompanyName()))
                .append(" | Share link expires ")
                .append(formatDateTime(report.getLinkExpiresAt()))
                .append(".");
        html.append("</div>");

        html.append("</div></body></html>");
        return html.toString();
    }

    private void appendMetaPill(StringBuilder html, String value) {
        html.append("<span class=\"meta-pill\">").append(escape(value)).append("</span>");
    }

    private void appendKpiCard(StringBuilder html, String label, String value, String detail) {
        html.append("<td class=\"kpi-card\">")
                .append("<div class=\"kpi-label\">").append(escape(label)).append("</div>")
                .append("<div class=\"kpi-value\">").append(escape(value)).append("</div>")
                .append("<div class=\"muted\">").append(escape(detail)).append("</div>")
                .append("</td>");
    }

    private void appendForecast(StringBuilder html, PublicClientReportResponse report) {
        if (report.getForecast() == null) {
            html.append("<div class=\"muted\">No forecast signals were available for this report window.</div>");
            return;
        }

        html.append("<table class=\"forecast-grid\"><tr>");
        appendForecastCard(html, report.getForecast().getNextPostPrediction());
        appendForecastCard(html, report.getForecast().getNextBestSlot());
        appendForecastCard(html, report.getForecast().getPlanningWindowProjection());
        html.append("</tr></table>");
        html.append("<div class=\"muted\">").append(escape(report.getForecast().getBasisNote())).append("</div>");
    }

    private void appendForecastCard(StringBuilder html, ClientReportForecastItemResponse item) {
        html.append("<td class=\"forecast-card\">");
        html.append("<div class=\"kpi-label\">").append(escape(item != null ? item.getLabel() : "Forecast")).append("</div>");
        html.append("<div class=\"kpi-value\">").append(escape(forecastHeadline(item))).append("</div>");
        html.append("<div class=\"muted\">").append(escape(forecastDetail(item))).append("</div>");
        html.append("</td>");
    }

    private void appendPlatformTable(StringBuilder html, List<ClientReportPlatformPerformanceResponse> platforms) {
        if (platforms.isEmpty()) {
            html.append("<div class=\"muted\">No platform analytics were available for this report window.</div>");
            return;
        }

        html.append("""
                <table class="data-table">
                    <thead>
                        <tr>
                            <th>Platform</th>
                            <th>Impressions</th>
                            <th>Engagements</th>
                            <th>Avg / Post</th>
                            <th>Share</th>
                        </tr>
                    </thead>
                    <tbody>
                """);

        for (ClientReportPlatformPerformanceResponse platform : platforms) {
            html.append("<tr>")
                    .append("<td>").append(escape(platform.getPlatformLabel())).append("</td>")
                    .append("<td>").append(escape(formatNumber(platform.getImpressions()))).append("</td>")
                    .append("<td>").append(escape(formatNumber(platform.getEngagements()))).append("</td>")
                    .append("<td>").append(escape(formatMaybeNumber(platform.getAverageEngagementsPerPost()))).append("</td>")
                    .append("<td>").append(escape(formatMaybePercent(platform.getEngagementSharePercent()))).append("</td>")
                    .append("</tr>");
        }

        html.append("</tbody></table>");
    }

    private void appendCampaignInsight(StringBuilder html, ClientReportCampaignInsightResponse campaignInsight) {
        if (campaignInsight == null || campaignInsight.getPostsPublished() == 0L) {
            html.append("<div class=\"muted\">This campaign does not yet have enough tracked post data in the selected window to render a benchmark view.</div>");
            return;
        }

        html.append("<table class=\"insight-grid\"><tr>");
        html.append("<td class=\"insight-card\">")
                .append("<div class=\"kpi-label\">Campaign Benchmark</div>")
                .append("<div class=\"kpi-value\">").append(escape(formatMaybePercent(campaignInsight.getPercentile()))).append("</div>")
                .append("<div class=\"muted\">Percentile rank across comparable campaigns. Lift vs benchmark: ")
                .append(escape(formatMaybePercent(campaignInsight.getLiftPercent())))
                .append("</div>")
                .append("</td>");
        html.append("<td class=\"insight-card\">")
                .append("<div class=\"kpi-label\">Campaign Output</div>")
                .append("<div class=\"kpi-value\">").append(escape(formatNumber(campaignInsight.getPostsPublished()))).append("</div>")
                .append("<div class=\"muted\">")
                .append(escape(formatNumber(Math.round(campaignInsight.getEngagements()))))
                .append(" engagements across the campaign in this report window.</div>")
                .append("</td>");
        html.append("</tr></table>");

        appendContributionTable(html, "Platform Contribution", campaignInsight.getPlatformBreakdown());
        appendContributionTable(html, "Account Contribution", campaignInsight.getAccountBreakdown());
    }

    private void appendContributionTable(StringBuilder html, String title, ClientReportContributionResponse contribution) {
        if (contribution == null || contribution.getRows().isEmpty()) {
            return;
        }

        html.append("<h3>").append(escape(title)).append("</h3>");
        html.append("""
                <table class="data-table">
                    <thead>
                        <tr>
                            <th>Segment</th>
                            <th>Posts</th>
                            <th>Performance</th>
                            <th>Avg / Post</th>
                        </tr>
                    </thead>
                    <tbody>
                """);

        for (ClientReportContributionRowResponse row : contribution.getRows()) {
            html.append("<tr>")
                    .append("<td>").append(escape(row.getLabel())).append("</td>")
                    .append("<td>").append(escape(formatNumber(row.getPostsPublished()))).append("</td>")
                    .append("<td>").append(escape(formatNumber(Math.round(row.getPerformanceValue())))).append("</td>")
                    .append("<td>").append(escape(formatMaybeNumber(row.getAveragePerformancePerPost()))).append("</td>")
                    .append("</tr>");
        }

        html.append("</tbody></table>");
    }

    private void appendTopPosts(StringBuilder html, List<ClientReportTopPostResponse> topPosts) {
        if (topPosts.isEmpty()) {
            html.append("<div class=\"muted\">No published post snapshots were available for this report window.</div>");
            return;
        }

        for (ClientReportTopPostResponse post : topPosts) {
            html.append("<div class=\"top-post\">");
            html.append("<div><strong>").append(escape(post.getPlatformLabel())).append("</strong>");
            if (post.getPublishedAt() != null) {
                html.append(" <span class=\"muted\">| ").append(formatDate(post.getPublishedAt())).append("</span>");
            }
            html.append("</div>");
            html.append("<div style=\"margin-top:8px;\">")
                    .append(escape(StringUtils.hasText(post.getContent())
                            ? post.getContent()
                            : "No post caption was captured for this item."))
                    .append("</div>");
            html.append("<div class=\"muted\" style=\"margin-top:8px;\">Engagements ")
                    .append(escape(formatNumber(post.getEngagements() != null ? post.getEngagements() : 0L)))
                    .append(" | Impressions ")
                    .append(escape(formatNumber(post.getImpressions() != null ? post.getImpressions() : 0L)))
                    .append(" | Likes ")
                    .append(escape(formatNumber(post.getLikes() != null ? post.getLikes() : 0L)))
                    .append(" | Comments ")
                    .append(escape(formatNumber(post.getComments() != null ? post.getComments() : 0L)))
                    .append(" | Shares ")
                    .append(escape(formatNumber(post.getShares() != null ? post.getShares() : 0L)))
                    .append("</div>");
            html.append("</div>");
        }
    }

    private String forecastHeadline(ClientReportForecastItemResponse item) {
        if (item == null || !item.isAvailable()) {
            return "Not enough data";
        }
        if (item.getRange() == null || item.getRange().getExpectedValue() == null) {
            return item.getSlotLabel() != null ? item.getSlotLabel() : "Available";
        }
        return formatNumber(Math.round(item.getRange().getExpectedValue()));
    }

    private String forecastDetail(ClientReportForecastItemResponse item) {
        if (item == null) {
            return "No forecast available.";
        }
        if (!item.isAvailable()) {
            return item.getUnavailableReason() != null ? item.getUnavailableReason() : "No forecast available.";
        }

        StringBuilder detail = new StringBuilder();
        if (StringUtils.hasText(item.getSlotLabel())) {
            detail.append(item.getSlotLabel()).append(". ");
        }
        if (item.getRange() != null && item.getRange().getLowValue() != null && item.getRange().getHighValue() != null) {
            detail.append("Range ")
                    .append(formatNumber(Math.round(item.getRange().getLowValue())))
                    .append(" to ")
                    .append(formatNumber(Math.round(item.getRange().getHighValue())))
                    .append(". ");
        }
        if (StringUtils.hasText(item.getBasisSummary())) {
            detail.append(item.getBasisSummary());
        }
        return detail.toString().trim();
    }

    private String prettyTemplate(String templateType) {
        if (templateType == null || templateType.isBlank()) {
            return "Client report";
        }
        String[] parts = templateType.replace('_', ' ').toLowerCase(Locale.ENGLISH).split("\\s+");
        StringBuilder pretty = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!pretty.isEmpty()) {
                pretty.append(' ');
            }
            pretty.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                pretty.append(part.substring(1));
            }
        }
        return pretty.isEmpty() ? "Client report" : pretty.toString();
    }

    private String formatNumber(long value) {
        return String.format(Locale.ENGLISH, "%,d", value);
    }

    private String formatMaybeNumber(Double value) {
        if (value == null) {
            return "Not available";
        }
        return String.format(Locale.ENGLISH, "%,.1f", value);
    }

    private String formatPercent(double value) {
        return String.format(Locale.ENGLISH, "%.2f%%", value);
    }

    private String formatMaybePercent(Double value) {
        if (value == null) {
            return "Not available";
        }
        return formatPercent(value);
    }

    private String formatDateTime(OffsetDateTime value) {
        return value == null ? "Not available" : value.withOffsetSameInstant(ZoneOffset.UTC).format(DATE_TIME_FORMATTER);
    }

    private String formatDate(OffsetDateTime value) {
        return value == null ? "Not available" : value.withOffsetSameInstant(ZoneOffset.UTC).format(DATE_FORMATTER);
    }

    private String formatDate(LocalDate value) {
        return value == null ? "Not available" : value.format(DATE_FORMATTER);
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }

    private String escapeAttribute(String value) {
        return escape(value).replace("\"", "&quot;");
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
