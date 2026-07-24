package com.ycy.aiapplication.rag.core.intent;

import com.ycy.aiapplication.rag.config.RAGIntentProperties;
import com.ycy.aiapplication.rag.core.intent.classifier.impl.FirstLayerIntentClassifier;
import com.ycy.aiapplication.rag.core.intent.classifier.impl.SecondLayerIntentClassifier;
import com.ycy.aiapplication.rag.core.intent.common.IntentNode;
import com.ycy.aiapplication.rag.core.intent.common.NodeScore;
import com.ycy.aiapplication.rag.core.intent.common.SubQuestionIntent;
import com.ycy.aiapplication.rag.core.intent.common.enums.IntentKind;
import com.ycy.aiapplication.rag.core.rewrite.common.RewriteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntentResolverTest {

    private FirstLayerIntentClassifier firstLayer;
    private SecondLayerIntentClassifier secondLayer;
    private IntentResolver resolver;

    @BeforeEach
    void setUp() {
        firstLayer = mock(FirstLayerIntentClassifier.class);
        secondLayer = mock(SecondLayerIntentClassifier.class);
        RAGIntentProperties properties = new RAGIntentProperties();
        properties.setFirstLayerMinScore(0.55D);
        properties.setKnowledgeMinScore(0.45D);
        properties.setKnowledgeGlobalMinScore(0.20D);
        properties.setKnowledgeTopN(3);
        resolver = new IntentResolver(firstLayer, secondLayer, properties);
    }

    @Test
    void shouldUseDirectedKnowledgeBaseWhenSecondLayerScoreIsHigh() {
        stubFirstLayerAsRag();
        NodeScore score = kbScore(0.72D);
        when(secondLayer.classifyTargets("Java并发")).thenReturn(List.of(score));

        SubQuestionIntent result = resolve("Java并发");

        assertEquals(IntentKind.KB, result.routeKind());
        assertFalse(result.globalKbFallback());
        assertEquals(List.of(score), result.nodeScores());
    }

    @Test
    void shouldUseGlobalKnowledgeBaseOnlyForCredibleWeakRelevance() {
        stubFirstLayerAsRag();
        when(secondLayer.classifyTargets("并发编程概念")).thenReturn(List.of(kbScore(0.30D)));

        SubQuestionIntent result = resolve("并发编程概念");

        assertEquals(IntentKind.KB, result.routeKind());
        assertTrue(result.globalKbFallback());
    }

    @Test
    void shouldRouteOutOfScopeTechnicalRequestToSystemInsteadOfGlobalSearch() {
        stubFirstLayerAsRag();
        when(secondLayer.classifyTargets("编写Vue登录页面")).thenReturn(List.of(kbScore(0.10D)));

        SubQuestionIntent result = resolve("编写Vue登录页面");

        assertEquals(IntentKind.SYSTEM, result.routeKind());
        assertFalse(result.globalKbFallback());
    }

    private void stubFirstLayerAsRag() {
        when(firstLayer.decide(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(FirstLayerIntentDecision.builder()
                        .ragScore(0.90D)
                        .systemScore(0.10D)
                        .shouldRetrieveKnowledgeBase(true)
                        .nodeScores(List.of())
                        .build());
    }

    private SubQuestionIntent resolve(String question) {
        return resolver.resolve(RewriteResult.success(question, List.of(question))).get(0);
    }

    private NodeScore kbScore(double score) {
        return NodeScore.builder()
                .node(IntentNode.builder()
                        .id("kb-java")
                        .name("Java")
                        .kind(IntentKind.KB)
                        .build())
                .score(score)
                .build();
    }
}
