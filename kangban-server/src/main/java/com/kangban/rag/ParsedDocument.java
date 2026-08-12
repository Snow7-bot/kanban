package com.kangban.rag;

import java.util.List;

public record ParsedDocument(List<ParsedPage> pages) {
    public ParsedDocument {
        pages = pages == null ? List.of() : List.copyOf(pages);
    }

    public String text() {
        return pages.stream().map(ParsedPage::text).filter(s -> !s.isBlank()).reduce("", (a, b) ->
                a.isBlank() ? b : a + "\n\n" + b);
    }
}
