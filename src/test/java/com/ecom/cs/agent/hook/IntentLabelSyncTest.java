package com.ecom.cs.agent.hook;

import com.ecom.cs.agent.intent.IntentCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 意图标签一致性护栏。
 *
 * <p>ProgressNotifierHook 里的 {@code INTENT_LABELS} 用于把意图 code 格式化成中文短标签，
 * 一旦与 {@link IntentCategory} 的 code 集合漂移，进度面板就会直接显示英文 code。
 * 本测试在编译期之外加一道运行时护栏。</p>
 */
@DisplayName("意图标签与 IntentCategory 一致性护栏")
class IntentLabelSyncTest {

    @SuppressWarnings("unchecked")
    private static Map<String, String> intentLabels() throws Exception {
        Field f = ProgressNotifierHook.class.getDeclaredField("INTENT_LABELS");
        f.setAccessible(true);
        return (Map<String, String>) f.get(null);
    }

    @Test
    @DisplayName("INTENT_LABELS 的键集合与 IntentCategory 的 code 集合完全一致")
    void labelsCoverAllIntentCodes() throws Exception {
        Set<String> expected = Arrays.stream(IntentCategory.values())
                .map(IntentCategory::getCode)
                .collect(Collectors.toSet());
        Set<String> actual = intentLabels().keySet();

        assertEquals(expected, actual,
                "INTENT_LABELS 与 IntentCategory 不一致，新增或重命名意图后必须同步标签表");
    }

    @Test
    @DisplayName("每个意图都有非空中文标签")
    void labelsAreNonBlank() throws Exception {
        intentLabels().forEach((code, label) ->
                assertTrue(label != null && !label.isBlank(), "意图 " + code + " 缺少中文标签"));
    }
}
