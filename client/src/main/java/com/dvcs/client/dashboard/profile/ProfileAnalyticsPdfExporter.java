package com.dvcs.client.dashboard.profile;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

public final class ProfileAnalyticsPdfExporter {

    private static final DateTimeFormatter EXPORTED_AT_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm").withZone(ZoneId.systemDefault());
    private static final float PAGE_MARGIN = 42f;
    private static final float SECTION_GAP = 18f;

    private ProfileAnalyticsPdfExporter() {
    }

    public static void export(
            Path outputPath,
            ProfileService.ProfileViewModel profile,
            List<SectionImage> sectionImages) throws IOException {
        Objects.requireNonNull(outputPath, "outputPath");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(sectionImages, "sectionImages");

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            float currentY;

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                currentY = page.getMediaBox().getHeight() - PAGE_MARGIN;
                currentY = writeHeader(content, page, profile, currentY);
                currentY = writeSummary(content, profile, currentY);
            }

            for (SectionImage sectionImage : sectionImages) {
                if (sectionImage == null || sectionImage.image() == null) {
                    continue;
                }

                PDPage currentPage = page;
                float pageWidth = currentPage.getMediaBox().getWidth();
                float usableWidth = pageWidth - (PAGE_MARGIN * 2);
                float imageWidth = sectionImage.image().getWidth();
                float imageHeight = sectionImage.image().getHeight();
                float scale = Math.min(usableWidth / imageWidth, 1f);
                float renderedHeight = imageHeight * scale;
                float requiredHeight = 20f + renderedHeight + SECTION_GAP;

                if (currentY - requiredHeight < PAGE_MARGIN) {
                    currentPage = new PDPage(PDRectangle.A4);
                    document.addPage(currentPage);
                    currentY = currentPage.getMediaBox().getHeight() - PAGE_MARGIN;
                    page = currentPage;
                }

                try (PDPageContentStream content = new PDPageContentStream(
                        document,
                        currentPage,
                        PDPageContentStream.AppendMode.APPEND,
                        true,
                        true)) {
                    content.beginText();
                    content.setFont(PDType1Font.HELVETICA_BOLD, 14);
                    content.newLineAtOffset(PAGE_MARGIN, currentY);
                    content.showText(sectionImage.title());
                    content.endText();

                    PDImageXObject image = LosslessFactory.createFromImage(document, sectionImage.image());
                    float imageY = currentY - 12f - renderedHeight;
                    content.drawImage(image, PAGE_MARGIN, imageY, imageWidth * scale, renderedHeight);
                }

                currentY -= requiredHeight;
            }

            document.save(outputPath.toFile());
        }
    }

    private static float writeHeader(
            PDPageContentStream content,
            PDPage page,
            ProfileService.ProfileViewModel profile,
            float y) throws IOException {
        float headerHeight = 72f;
        content.setNonStrokingColor(18, 41, 30);
        content.addRect(PAGE_MARGIN, y - headerHeight, page.getMediaBox().getWidth() - (PAGE_MARGIN * 2), headerHeight);
        content.fill();

        content.beginText();
        content.setNonStrokingColor(236, 253, 245);
        content.setFont(PDType1Font.HELVETICA_BOLD, 22);
        content.newLineAtOffset(PAGE_MARGIN + 18f, y - 28f);
        content.showText("Analytics Report");
        content.endText();

        content.beginText();
        content.setFont(PDType1Font.HELVETICA, 11);
        content.newLineAtOffset(PAGE_MARGIN + 18f, y - 48f);
        content.showText("User: " + displayName(profile));
        content.endText();

        content.beginText();
        content.setFont(PDType1Font.HELVETICA, 11);
        content.newLineAtOffset(PAGE_MARGIN + 220f, y - 48f);
        content.showText("Exported: " + EXPORTED_AT_FMT.format(java.time.Instant.now()));
        content.endText();

        return y - headerHeight - 24f;
    }

    private static float writeSummary(
            PDPageContentStream content,
            ProfileService.ProfileViewModel profile,
            float y) throws IOException {
        String[] lines = {
                "Username: @" + safe(profile.username()),
                "Workspaces: " + profile.totalWorkspaces()
                        + "   Folders: " + profile.totalFolders()
                        + "   Files: " + profile.totalFiles(),
                "Commits: " + profile.totalCommits(),
                "Storage Used: " + formatBytes(profile.storageUsedBytes())
        };

        content.setNonStrokingColor(220, 252, 231);
        for (String line : lines) {
            content.beginText();
            content.setFont(PDType1Font.HELVETICA, 11);
            content.newLineAtOffset(PAGE_MARGIN, y);
            content.showText(line);
            content.endText();
            y -= 16f;
        }
        return y - 8f;
    }

    private static String displayName(ProfileService.ProfileViewModel profile) {
        String name = safe(profile.name());
        return name.isBlank() ? "@" + safe(profile.username()) : name + " (@" + safe(profile.username()) + ")";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String formatBytes(long bytes) {
        if (bytes >= 1_073_741_824L) {
            return String.format("%.2f GB", bytes / 1_073_741_824.0);
        }
        if (bytes >= 1_048_576L) {
            return String.format("%.1f MB", bytes / 1_048_576.0);
        }
        if (bytes >= 1024L) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return bytes + " B";
    }

    public record SectionImage(String title, BufferedImage image) {}
}
