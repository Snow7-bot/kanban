package com.kangban.rag;

public record ParsedPage(Integer pageNumber, String section, String text) {
    public ParsedPage {
        section = section == null ? "" : section;
        text = text == null ? "" : text.trim();
    }
}
