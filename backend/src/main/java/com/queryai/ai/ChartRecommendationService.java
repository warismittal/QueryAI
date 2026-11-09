package com.queryai.ai;

import com.queryai.ai.ChartRecommender.Column;
import com.queryai.ai.ChartRecommender.Kind;
import com.queryai.ai.ChartRecommender.Recommendation;
import com.queryai.sql.QueryResult;
import org.springframework.stereotype.Service;

/** Chooses an appropriate visualisation for a real query result. */
@Service
public class ChartRecommendationService {

    public Recommendation recommend(QueryResult result) {
        if (result == null) {
            return ChartRecommender.recommend(java.util.List.of(), java.util.List.of());
        }
        return ChartRecommender.recommend(result.columns().stream()
                .map(c -> new Column(c.name(), Kind.valueOf(c.category().name()))).toList(), result.rows());
    }
}
