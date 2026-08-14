package com.kangban.rag;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextDocumentParserTest {

    private final TextDocumentParser parser = new TextDocumentParser();

    @Test
    void parsesMarkdownSectionsAndKeepsChineseText() {
        ParsedDocument document = parser.parse(
                "血压.md", "text/markdown",
                "# 血压记录\n\n建议每天固定时间测量血压。\n# 就医提醒\n\n持续异常请及时就医。"
                        .getBytes(StandardCharsets.UTF_8));

        assertThat(document.pages()).hasSize(2);
        assertThat(document.pages().get(0).section()).isEqualTo("血压记录");
        assertThat(document.text()).contains("固定时间测量血压");
    }

    @Test
    void rejectsUnsupportedImageAndOcrFiles() {
        assertThatThrownBy(() -> parser.parse("scan.png", "image/png", new byte[]{1}))
                .hasMessageContaining("OCR 暂未接入");
    }
}
