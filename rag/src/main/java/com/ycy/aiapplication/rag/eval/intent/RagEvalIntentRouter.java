package com.ycy.aiapplication.rag.eval.intent;

import com.ycy.aiapplication.rag.core.intent.IntentResolver;
import com.ycy.aiapplication.rag.core.intent.common.SubQuestionIntent;
import com.ycy.aiapplication.rag.core.rewrite.common.RewriteResult;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/** Selects the intent strategy used only by eval endpoints. */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rag-eval.enabled", havingValue = "true")
public class RagEvalIntentRouter {

    private final IntentResolver layeredIntentResolver;
    private final RagEvalCombinedIntentResolver combinedIntentResolver;

    public List<SubQuestionIntent> resolve(RewriteResult rewriteResult, RagEvalIntentMode mode) {
        return mode == RagEvalIntentMode.LAYERED
                ? layeredIntentResolver.resolve(rewriteResult)
                : combinedIntentResolver.resolve(rewriteResult);
    }
}
