package com.tonyghouse.socialraven.service.reporting;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.tonyghouse.socialraven.dto.analytics.AnalyticsOverviewResponse;
import com.tonyghouse.socialraven.dto.analytics.PlatformStatsResponse;
import com.tonyghouse.socialraven.dto.analytics.TopPostResponse;
import com.tonyghouse.socialraven.dto.reporting.PublicClientReportResponse;
import com.tonyghouse.socialraven.exception.SocialRavenException;
import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
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
        appendMetaPill(html, "Generated " + formatDateTime(report.getGeneratedAt()));
        html.append("</div>");
        html.append("</td><td class=\"logo-box\">");
        if (report.getLogoUrl() != null && !report.getLogoUrl().isBlank()) {
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
        appendKpiCard(html, "Impressions", formatNumber(report.getOverview().getTotalImpressions()), "Reach " + formatNumber(report.getOverview().getTotalReach()));
        appendKpiCard(html, "Engagements", formatNumber(totalEngagements(report.getOverview())), formatPercent(report.getOverview().getAvgEngagementRate()) + " avg engagement");
        appendKpiCard(html, "Follower Growth", formatNumber(report.getOverview().getFollowerGrowth()), "Audience momentum across connected accounts");
        appendKpiCard(html, "Posts Published", formatNumber(report.getOverview().getTotalPosts()), "Published output in this window");
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
        html.append("<h2>Platform Performance</h2>");
        appendPlatformTable(html, safeList(report.getPlatformStats()));
        html.append("</div>");

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

    private void appendPlatformTable(StringBuilder html, List<PlatformStatsResponse> platforms) {
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
                            <th>Follower Growth</th>
                            <th>Posts</th>
                        </tr>
                    </thead>
                    <tbody>
                """);

        for (PlatformStatsResponse platform : platforms) {
            html.append("<tr>")
                    .append("<td>").append(escape(platform.getProvider())).append("</td>")
                    .append("<td>").append(escape(formatNumber(platform.getImpressions()))).append("</td>")
                    .append("<td>")
                    .append(escape(formatNumber(totalEngagements(platform))))
                    .append(" | ")
                    .append(escape(formatPercent(platform.getEngagementRate())))
                    .append("</td>")
                    .append("<td>").append(escape(formatNumber(platform.getFollowerGrowth()))).append("</td>")
                    .append("<td>").append(escape(formatNumber(platform.getPostsPublished()))).append("</td>")
                    .append("</tr>");
        }

        html.append("</tbody></table>");
    }

    private void appendTopPosts(StringBuilder html, List<TopPostResponse> topPosts) {
        if (topPosts.isEmpty()) {
            html.append("<div class=\"muted\">No published post snapshots were available for this report window.</div>");
            return;
        }

        for (TopPostResponse post : topPosts) {
            html.append("<div class=\"top-post\">");
            html.append("<div><strong>").append(escape(post.getProvider())).append("</strong>");
            if (post.getPublishedAt() != null) {
                html.append(" <span class=\"muted\">| ").append(formatDate(post.getPublishedAt())).append("</span>");
            }
            html.append("</div>");
            html.append("<div style=\"margin-top:8px;\">")
                    .append(escape(post.getContent() != null ? post.getContent() : "No post caption was captured for this item."))
                    .append("</div>");
            html.append("<div class=\"muted\" style=\"margin-top:8px;\">Impressions ")
                    .append(escape(formatNumber(post.getImpressions())))
                    .append(" | Engagement ")
                    .append(escape(formatPercent(post.getEngagementRate())))
                    .append(" | Comments ")
                    .append(escape(formatNumber(post.getComments())))
                    .append(" | Shares ")
                    .append(escape(formatNumber(post.getShares())))
                    .append("</div>");
            html.append("</div>");
        }
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

    private String formatPercent(double value) {
        return String.format(Locale.ENGLISH, "%.2f%%", value);
    }

    private String formatDateTime(OffsetDateTime value) {
        return value == null ? "Not available" : value.withOffsetSameInstant(ZoneOffset.UTC).format(DATE_TIME_FORMATTER);
    }

    private String formatDate(OffsetDateTime value) {
        return value == null ? "Not available" : value.withOffsetSameInstant(ZoneOffset.UTC).format(DATE_FORMATTER);
    }

    private long totalEngagements(AnalyticsOverviewResponse overview) {
        return overview.getTotalLikes() + overview.getTotalComments() + overview.getTotalShares();
    }

    private long totalEngagements(PlatformStatsResponse platform) {
        return platform.getLikes() + platform.getComments() + platform.getShares();
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
