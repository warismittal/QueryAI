package com.queryai.ai;

import com.queryai.schema.GlossaryService;
import com.queryai.schema.GlossaryTerm;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Finds the business definitions that apply to a question so the generated SQL follows company semantics. */
@Service
public class GlossaryRetrievalService {
    private static final int ALWAYS_INCLUDE_BELOW = 12;
    private final GlossaryService glossaryService;

    public GlossaryRetrievalService(GlossaryService glossaryService) {
        this.glossaryService = glossaryService;
    }

    public List<GlossaryTerm> relevantTerms(Long dataSourceId, String question, List<String> keywords) {
        List<GlossaryTerm> all = glossaryService.terms(dataSourceId);
        if (all.size() <= ALWAYS_INCLUDE_BELOW) {
            return all;
        }
        Set<String> q = new HashSet<>(SchemaRanker.tokens(question));
        if (keywords != null) {
            keywords.forEach(k -> q.addAll(SchemaRanker.tokens(k)));
        }
        List<GlossaryTerm> matches = new ArrayList<>();
        for (GlossaryTerm t : all) {
            if (SchemaRanker.tokens(t.getTerm()).stream().anyMatch(q::contains)) {
                matches.add(t);
            }
        }
        return matches;
    }

    public static String render(List<GlossaryTerm> terms) {
        if (terms.isEmpty()) {
            return "(no business glossary defined)";
        }
        StringBuilder sb = new StringBuilder();
        for (GlossaryTerm t : terms) {
            sb.append("- ").append(t.getTerm()).append(": ").append(t.getDefinition());
            if (t.getSqlHint() != null) {
                sb.append(" [SQL: ").append(t.getSqlHint()).append(']');
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
