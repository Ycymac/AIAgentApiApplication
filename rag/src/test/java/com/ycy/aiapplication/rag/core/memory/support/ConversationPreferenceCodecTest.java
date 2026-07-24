package com.ycy.aiapplication.rag.core.memory.support;

import com.ycy.aiapplication.rag.core.memory.common.PreferenceItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConversationPreferenceCodecTest {

    @Test
    void shouldAppendStableIdsAndSkipExactDuplicates() {
        List<PreferenceItem> result = ConversationPreferenceCodec.append(
                List.of(new PreferenceItem("100-1", "100", "分点回答")),
                "200",
                List.of("分点回答", "说明底层原理")
        );

        assertEquals(2, result.size());
        assertEquals("200-2", result.get(1).getId());
        assertEquals("说明底层原理", result.get(1).getContent());
    }

    @Test
    void shouldKeepPreferencesAddedWhileCompressionWasRunning() {
        PreferenceItem original = new PreferenceItem("100-1", "100", "详细回答");
        PreferenceItem rewritten = new PreferenceItem("100-1", "100", "回答时提供必要细节");
        PreferenceItem addedLater = new PreferenceItem("200-1", "200", "使用中文");

        List<PreferenceItem> merged = ConversationPreferenceCodec.mergeChangesAfterSnapshot(
                List.of(rewritten),
                List.of(original),
                List.of(original, addedLater)
        );

        assertEquals(List.of(rewritten, addedLater), merged);
    }
}
