package com.ghouse.socialraven.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BatchUtil {

    private BatchUtil() {
        // utility class
    }

    public static <T> List<List<T>> partition(List<T> list, int batchSize) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be > 0");
        }

        List<List<T>> batches = new ArrayList<>();

        for (int i = 0; i < list.size(); i += batchSize) {
            batches.add(
                    list.subList(i, Math.min(i + batchSize, list.size()))
            );
        }

        return batches;
    }
}
