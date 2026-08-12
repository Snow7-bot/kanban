package com.kangban.rag;

import com.kangban.common.BusinessException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class TextDocumentParser implements DocumentParser {

    @Override
    public ParsedDocument parse(String fileName, String mediaType, byte[] bytes) {
        String extension = extension(fileName);
        return switch (extension) {
            case "txt", "md", "markdown" -> parseText(new String(bytes, StandardCharsets.UTF_8), extension);
            case "pdf" -> parsePdf(bytes);
            default -> throw BusinessException.paramsError(
                    "当前仅支持 TXT、Markdown 和文本型 PDF，扫描图片/OCR 暂未接入");
        };
    }

    private ParsedDocument parseText(String text, String extension) {
        if (text == null || text.isBlank()) {
            throw BusinessException.paramsError("文档内容不能为空");
        }
        List<ParsedPage> pages = new ArrayList<>();
        String section = "";
        StringBuilder buffer = new StringBuilder();
        for (String line : text.replace("\r\n", "\n").split("\n", -1)) {
            String trimmed = line.trim();
            if (extension.equals("md") || extension.equals("markdown")) {
                if (trimmed.startsWith("#")) {
                    if (!buffer.isEmpty()) {
                        pages.add(new ParsedPage(null, section, buffer.toString()));
                        buffer.setLength(0);
                    }
                    section = trimmed.replaceFirst("^#+\\s*", "").trim();
                }
            }
            if (!trimmed.isEmpty() || !buffer.isEmpty()) {
                buffer.append(line).append('\n');
            }
        }
        if (!buffer.isEmpty()) {
            pages.add(new ParsedPage(null, section, buffer.toString()));
        }
        return new ParsedDocument(pages);
    }

    private ParsedDocument parsePdf(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            List<ParsedPage> pages = new ArrayList<>();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document).trim();
                if (!text.isBlank()) {
                    pages.add(new ParsedPage(page, "", text));
                }
            }
            if (pages.isEmpty()) {
                throw BusinessException.paramsError("PDF 未提取到文本，扫描版 PDF/OCR 暂未接入");
            }
            return new ParsedDocument(pages);
        } catch (IOException e) {
            throw BusinessException.paramsError("PDF 文档解析失败");
        }
    }

    static String extension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        String normalized = fileName.replace('\\', '/');
        int dot = normalized.lastIndexOf('.');
        return dot < 0 ? "" : normalized.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
