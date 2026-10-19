package com.ecom.cs.config;

import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.embedding.ollama.OllamaTextEmbedding;
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.integration.bailian.BailianConfig;
import io.agentscope.core.rag.integration.bailian.BailianKnowledge;
import io.agentscope.core.rag.knowledge.SimpleKnowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.reader.ReaderInput;
import io.agentscope.core.rag.reader.SplitStrategy;
import io.agentscope.core.rag.reader.TableFormat;
import io.agentscope.core.rag.reader.TextReader;
import io.agentscope.core.rag.reader.WordReader;
import io.agentscope.core.rag.store.InMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 景点 RAG 知识库装配配置。
 *
 * @author Hollis
 */
@Configuration
public class RagKnowledgeConfig {

    private static final Logger log = LoggerFactory.getLogger(RagKnowledgeConfig.class);

    /**
     * bge-m3 的 dense 向量为 1024 维；显式声明并同时传给 embedding 与 InMemoryStore，
     * 避免将来升级 embedding 模型/维度时两端静默错位（与 {@link IntentRouterKnowledgeConfig} 同约定）。
     */
    private static final int EMBEDDING_DIM = 1024;

    /**
     * 本地 Ollama 地址与 embedding 模型名（替代收费的 DashScope text-embedding-v4）。
     */
    @Value("${ollama.base-url:${OLLAMA_BASE_URL:http://192.168.3.15:11434}}")
    private String ollamaBaseUrl;

    @Value("${ollama.embedding-model:${OLLAMA_EMBEDDING_MODEL:bge-m3}}")
    private String ollamaEmbeddingModel;

    @Value("${agentscope.dashscope.api-key}")
    private String apiKey;

    @Value("${agentscope.bailian.access-key-id}")
    private String accessKeyId;

    @Value("${agentscope.bailian.access-key-secret}")
    private String accessKeySecret;

    @Value("${agentscope.bailian.workspace-id}")
    private String workspaceId;

    @Value("${agentscope.bailian.index-id}")
    private String indexId;

    /**
     * 本地 docx 语料构建失败时是否中断启动。
     * <p>
     * 默认 true（保持原有 fail-fast 语义）。设为 false 时，语料缺失 / embedding 调用失败
     * 只打印告警并返回空知识库，服务仍可启动，便于在没有可用 DashScope Key
     * 或离线环境下先把服务跑起来（RAG 相关能力会不可用）。
     */
    @Value("${app.rag.fail-fast:true}")
    private boolean ragFailFast;

    @Bean(name = "attractionKnowledge")
    public Knowledge attractionKnowledge(
            @Value("classpath:dataset/tourist_attractions.md") Resource attractionsResource) {
        if (isBailianConfigured()) {
            // 百炼 index 真实配置时走云端知识库（DashScope Bailian 检索）
            BailianConfig config =
                    BailianConfig.builder()
                            .accessKeyId(accessKeyId)
                            .accessKeySecret(accessKeySecret)
                            .workspaceId(workspaceId)
                            .build();

            return BailianKnowledge.builder()
                    .config(config)
                    .indexId(indexId)
                    .build();
        }
        // 未配置百炼（默认）：用本地语料 tourist_attractions.md 构建
        // Ollama bge-m3 + InMemoryStore 的内存版 RAG，与 docx 知识库同一套技术栈
        return buildTextKnowledge(attractionsResource, "dataset/tourist_attractions.md", 600);
    }

    /**
     * 判断百炼知识库是否真实配置：index-id 为空或仍是模板占位符（"xxxx"）时视为未配置，
     * 回退到本地语料构建，避免用占位凭据调用百炼接口在运行期才报错。
     */
    private boolean isBailianConfigured() {
        return indexId != null && !indexId.isBlank() && !indexId.contains("xxxx");
    }

    @Bean(name = "corporateTravelPolicyKnowledge")
    public Knowledge corporateTravelPolicyKnowledge(@Value("classpath:dataset/business_travel_policy.docx") Resource policyResource) {
        return buildDocxKnowledge(policyResource, "dataset/business_travel_policy.docx", 400);
    }

    @Bean(name = "corporateTravelGuidelinesKnowledge")
    public Knowledge corporateTravelGuidelinesKnowledge(@Value("classpath:dataset/business_travel_guidelines.docx") Resource fileResource) {
        return buildDocxKnowledge(fileResource, "dataset/business_travel_guidelines.docx", 500);
    }

    /**
     * 用单个 docx 语料构建内存版 RAG 知识库：本地 Ollama bge-m3 embedding + InMemoryStore，
     * 启动期同步解析并写入向量库（语料小，fail-fast 优于运行期降级）。
     *
     * @param resource classpath 下的 docx 资源
     * @param label    资源相对路径，仅用于报错提示
     */
    private Knowledge buildDocxKnowledge(Resource resource, String label, Integer chunkSize) {
        SimpleKnowledge knowledge = newSimpleKnowledge();
        FixedWordReader reader = new FixedWordReader(chunkSize, SplitStrategy.PARAGRAPH, 0, true, TableFormat.MARKDOWN);
        try {
            // fat jar 内的 classpath 资源不是文件系统文件（resource.getFile() 会抛 FileNotFoundException），
            // 必须先经 getInputStream() 落到临时文件，再交给基于路径的 POI 类 reader
            File docxFile = extractToTempFile(resource, ".docx");
            List<Document> docs = reader.read(ReaderInput.fromPath(docxFile.toPath())).block();
            knowledge.addDocuments(docs).block();
            docxFile.delete();
        } catch (Exception e) {
            handleKnowledgeBuildFailure(label, e);
        }

        return knowledge;
    }

    /**
     * 用单个 Markdown/纯文本语料构建内存版 RAG 知识库（本地 Ollama bge-m3 + InMemoryStore），
     * 供 attractionKnowledge 在百炼未配置时作为本地知识库。
     */
    private Knowledge buildTextKnowledge(Resource resource, String label, Integer chunkSize) {
        SimpleKnowledge knowledge = newSimpleKnowledge();
        TextReader reader = new TextReader(chunkSize, SplitStrategy.PARAGRAPH, 0);
        try {
            // 注意两点：1) 必须用 getInputStream() 而非 getFile()——fat jar 内 classpath 资源不在文件系统；
            //          2) TextReader 必须配 fromString/fromFile 读入真实文本，
            //             fromPath 只是路径引用，会把路径字符串当作正文切分（实测 542KB 语料只切出 1 块空内容）
            String content;
            try (InputStream in = resource.getInputStream()) {
                content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            List<Document> docs = reader.read(ReaderInput.fromString(content)).block();
            knowledge.addDocuments(docs).block();
            log.info("本地景点知识库 [{}] 构建完成，文档分块数：{}", label, docs == null ? 0 : docs.size());
        } catch (Exception e) {
            handleKnowledgeBuildFailure(label, e);
        }

        return knowledge;
    }

    /** 把 classpath 资源（docx 等）解包到临时文件，供基于文件路径的 reader 使用。 */
    private File extractToTempFile(Resource resource, String suffix) throws IOException {
        File tmp = File.createTempFile("llmentor-", suffix);
        tmp.deleteOnExit();
        try (InputStream in = resource.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
            in.transferTo(out);
        }
        return tmp;
    }

    private SimpleKnowledge newSimpleKnowledge() {
        OllamaTextEmbedding embedding = OllamaTextEmbedding.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaEmbeddingModel)
                .dimensions(EMBEDDING_DIM)
                .build();

        return SimpleKnowledge.builder()
                .embeddingStore(InMemoryStore.builder().dimensions(EMBEDDING_DIM).build())
                .embeddingModel(embedding)
                .build();
    }

    private void handleKnowledgeBuildFailure(String label, Exception e) {
        if (ragFailFast) {
            // 资源缺失或解析失败时主动报错，提示开发者补齐语料
            throw new IllegalStateException(
                    "从 classpath 读取 [" + label + "] 失败，请确认文件存在于 src/main/resources/dataset/ 目录下。", e);
        }
        log.warn("构建知识库 [{}] 失败，已按 app.rag.fail-fast=false 降级为空知识库（RAG 能力不可用）：{}",
                label, e.getMessage());
    }

}
