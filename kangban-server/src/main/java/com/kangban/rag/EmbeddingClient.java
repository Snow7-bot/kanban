package com.kangban.rag;

import java.util.List;

public interface EmbeddingClient {
    double[] embed(String text);

    /** 批量接口；本地实现默认逐条执行，远程实现可覆盖为单次批量请求。 */
    default List<double[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        return texts.stream().map(this::embed).toList();
    }

    /** 当前向量维度，用于校验和解析异常数据。 */
    default int dimensions() {
        return -1;
    }
}
