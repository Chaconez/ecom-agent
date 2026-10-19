package com.ecom.cs.config;

import com.ecom.cs.agent.intent.IntentCategory;
import com.ecom.cs.agent.intent.IntentVectorMatcher;
import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.embedding.ollama.OllamaTextEmbedding;
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.knowledge.SimpleKnowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.DocumentMetadata;
import io.agentscope.core.rag.store.InMemoryStore;
import io.agentscope.core.message.TextBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 意图路由向量库装配配置。
 *
 * <p>独立于 {@link RagKnowledgeConfig} 中已有的景点/政策知识库，专门服务于三层意图识别策略
 * 的 L2（向量相似度）层。启动时把每种意图的代表问句批量写入 InMemoryStore + 本地 Ollama bge-m3 Embedding，
 * 运行期 L2 命中只需做一次 embedding 检索，延迟可控制在 100ms 以内（不含网络抖动）。</p>
 *
 * <p>本类暴露 3 个 Spring bean：
 * <ul>
 *   <li>{@code intentRouterEmbeddingModel}：与 RAG 知识库隔离的 embedding 实例，避免互相干扰；</li>
 *   <li>{@code intentRouterKnowledge}：注入 15 类意图代表问句的 {@link Knowledge}；</li>
 *   <li>{@code intentCategoryExamples}：Map&lt;IntentCategory, List&lt;String&gt;&gt;，便于
 *       L1 规则匹配器复用相同的高频问句表达。</li>
 * </ul>
 *
 * @author Hollis
 */
@Configuration
public class IntentRouterKnowledgeConfig {

    private static final Logger logger = LoggerFactory.getLogger(IntentRouterKnowledgeConfig.class);

    /**
     * bge-m3 的 dense 向量为 1024 维；与 InMemoryStore 默认 dimensions 一致。
     */
    private static final int EMBEDDING_DIM = 1024;

    @Value("${ollama.base-url:${OLLAMA_BASE_URL:http://192.168.3.15:11434}}")
    private String ollamaBaseUrl;

    @Value("${ollama.embedding-model:${OLLAMA_EMBEDDING_MODEL:bge-m3}}")
    private String ollamaEmbeddingModel;

    /**
     * 启动期 embedding 调用失败时是否中断启动。
     *
     * <p>默认 true（保持原有 fail-fast 语义）。设为 false 时只打印告警，降级为空知识库，
     * L2 向量召回不可用，但 L1 规则匹配与 L3 LLM 兜底仍正常工作
     * （{@link IntentVectorMatcher#match} 对检索异常已做兜底）。</p>
     */
    @Value("${app.rag.fail-fast:true}")
    private boolean ragFailFast;

    @Autowired
    private IntentSeedExamplesProperties seedExamplesProperties;

    private final Map<IntentCategory, List<String>> categoryExamples = new LinkedHashMap<>();

    @Bean(name = "intentRouterEmbeddingModel")
    public EmbeddingModel intentRouterEmbeddingModel() {
        // 本地 Ollama bge-m3：免费、1024 维 dense 向量，替代收费的 DashScope text-embedding-v4。
        // 模型未拉取（ollama pull bge-m3）或服务不可达时，启动期 embedding 会失败，
        // 由 app.rag.fail-fast 决定是否降级为空知识库。
        return OllamaTextEmbedding.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaEmbeddingModel)
                .dimensions(EMBEDDING_DIM)
                .build();
    }

    @Bean(name = "intentRouterKnowledge")
    public Knowledge intentRouterKnowledge(EmbeddingModel intentRouterEmbeddingModel) {
        InMemoryStore store = InMemoryStore.builder().dimensions(EMBEDDING_DIM).build();
        SimpleKnowledge knowledge = SimpleKnowledge.builder()
                .embeddingModel(intentRouterEmbeddingModel)
                .embeddingStore(store)
                .build();

        // 启动时同步填充；addDocuments 会触发 embedding 计算，阻塞等待完成
        List<Document> docs = buildSeedDocuments();
        try {
            knowledge.addDocuments(docs).block();
            logger.info("[INTENT_ROUTER] 意图路由向量库启动完成，预填充样本数={}", docs.size());
        } catch (Exception e) {
            if (ragFailFast) {
                throw new IllegalStateException(
                        "意图路由向量库预填充失败（embedding 调用异常），"
                                + "请检查 agentscope.dashscope.api-key 是否有效。", e);
            }
            logger.warn("[INTENT_ROUTER] 意图路由向量库预填充失败，已按 app.rag.fail-fast=false 降级为"
                    + "空知识库，L2 向量召回不可用（L1 规则匹配与 L3 LLM 兜底仍正常工作）：{}", e.getMessage());
        }
        return knowledge;
    }

    /**
     * 暴露每种意图的高频问句样本，便于 L1 规则匹配器共享同一套语料，
     * 也方便后续通过配置覆盖（暂未接入，保留扩展点）。
     */
    @Bean(name = "intentCategoryExamples")
    public Map<IntentCategory, List<String>> intentCategoryExamples() {
        Map<IntentCategory, List<String>> snapshot = new LinkedHashMap<>(categoryExamples);
        return snapshot;
    }

    /**
     * 显式装配 L2 向量匹配器。
     *
     * <p>不使用 {@code @Component} 让 Spring 扫描的原因：容器中已存在多个
     * {@link Knowledge} bean（{@link RagKnowledgeConfig} 暴露的景点 / 政策库 +
     * 本配置的意图路由库），构造函数参数自动注入会因类型不唯一而失败（启动期
     * 报 {@code BeanInstantiationException: No default constructor found}）。
     * 这里通过参数名 {@code intentRouterKnowledge} 精确指定依赖。</p>
     */
    @Bean(name = "intentVectorMatcher")
    public IntentVectorMatcher intentVectorMatcher(Knowledge intentRouterKnowledge) {
        return new IntentVectorMatcher(intentRouterKnowledge);
    }

    @PostConstruct
    void initSeedExamples() {
        // 设计原则：L2 是 L1（关键词/正则）的补充，只在 L1 未命中时才被调用。
        // 语料内容外置到 intent-seed.yml，剔除 L1 能直接命中的说法，
        // 只保留自然口语、整句、不含 L1 触发词的表达（详见该配置文件头部说明）。
        // 冗余护栏由 IntentRouterSeedExamplesTest#shouldNotOverlapWithL1Rules 离线校验。
        categoryExamples.clear();
        Map<String, List<String>> seed = seedExamplesProperties.getSeedExamples();
        if (seed == null || seed.isEmpty()) {
            logger.warn("[INTENT_ROUTER] 未加载到意图种子语料，请检查 intent-seed.yml 是否被正确 import");
            return;
        }
        for (Map.Entry<String, List<String>> entry : seed.entrySet()) {
            IntentCategory category = IntentCategory.fromCode(entry.getKey());
            categoryExamples.put(category, List.copyOf(entry.getValue()));
        }
    }

    private List<Document> buildSeedDocuments() {
        // 确保 PostConstruct 已把数据填充好
        if (categoryExamples.isEmpty()) {
            initSeedExamples();
        }

        List<Document> docs = new ArrayList<>();
        int chunkSeq = 0;
        for (Map.Entry<IntentCategory, List<String>> entry : categoryExamples.entrySet()) {
            IntentCategory category = entry.getKey();
            for (String example : entry.getValue()) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("intent", category.getCode());
                payload.put("target_agent", category.getDefaultTargetAgent());
                payload.put("confidence", IntentRecognitionConfidenceSeed.HIGH);

                DocumentMetadata metadata = DocumentMetadata.builder()
                        .content(TextBlock.builder().text(example).build())
                        .docId("intent-" + category.getCode())
                        .chunkId(String.valueOf(chunkSeq++))
                        .payload(payload)
                        .build();
                docs.add(new Document(metadata));
            }
        }
        return docs;
    }

    /**
     * 仅为占位，便于将来把"高置信度"字符串集中到一处。
     */
    private static final class IntentRecognitionConfidenceSeed {
        static final String HIGH = "high";
    }
}
