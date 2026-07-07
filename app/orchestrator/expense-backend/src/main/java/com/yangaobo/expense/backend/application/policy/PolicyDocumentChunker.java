package com.yangaobo.expense.backend.application.policy;

import com.yangaobo.expense.common.error.ExpenseFlowErrorCode;
import com.yangaobo.expense.common.error.ExpenseFlowException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PolicyDocumentChunker {

    private static final int MAX_CHARS = 1200;
    private static final int OVERLAP_CHARS = 120;

    public List<PolicyChunkDraft> chunk(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            throw new ExpenseFlowException(
                    ExpenseFlowErrorCode.VALIDATION_FAILED, "制度正文不能为空");
        }
        List<PolicyChunkDraft> result = new ArrayList<>();
        String section = "正文";
        StringBuilder body = new StringBuilder();
        for (String line : markdown.replace("\r\n", "\n").split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                flushSection(result, section, body.toString());
                section = trimmed.replaceFirst("^#+\\s*", "").trim();
                if (section.isBlank()) {
                    section = "未命名章节";
                }
                body.setLength(0);
            } else {
                body.append(line).append('\n');
            }
        }
        flushSection(result, section, body.toString());
        if (result.isEmpty()) {
            throw new ExpenseFlowException(
                    ExpenseFlowErrorCode.VALIDATION_FAILED, "制度正文没有可索引内容");
        }
        return List.copyOf(result);
    }

    private static void flushSection(
            List<PolicyChunkDraft> result, String section, String rawContent) {
        String content = rawContent.trim();
        if (content.isBlank()) {
            return;
        }
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(start + MAX_CHARS, content.length());
            if (end < content.length()) {
                int paragraphEnd = content.lastIndexOf('\n', end);
                if (paragraphEnd > start + MAX_CHARS / 2) {
                    end = paragraphEnd;
                }
            }
            String chunk = content.substring(start, end).trim();
            if (!chunk.isBlank()) {
                result.add(new PolicyChunkDraft(section, chunk, estimateTokens(chunk)));
            }
            if (end >= content.length()) {
                break;
            }
            start = Math.max(end - OVERLAP_CHARS, start + 1);
        }
    }

    private static int estimateTokens(String content) {
        long chineseCharacters =
                content.codePoints()
                        .filter(
                                codePoint ->
                                        Character.UnicodeScript.of(codePoint)
                                                == Character.UnicodeScript.HAN)
                        .count();
        long otherCharacters = content.length() - chineseCharacters;
        return Math.max(1, Math.toIntExact(chineseCharacters + (otherCharacters + 3) / 4));
    }
}
