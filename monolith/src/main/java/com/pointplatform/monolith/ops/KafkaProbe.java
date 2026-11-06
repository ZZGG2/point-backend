package com.pointplatform.monolith.ops;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Kafka 브로커 접속 + 원장 반영 컨슈머(point-ledger) 지연 확인.
 * consumerLag = 토픽 끝 offset − 커밋된 offset 합. 0 이면 발행된 보상이 모두 원장에 반영된 상태.
 */
@Component
class KafkaProbe {

    static final String TOPIC = "point.reward.requested";
    static final String GROUP = "point-ledger";
    private static final int TIMEOUT_MS = 2000;

    private final KafkaAdmin kafkaAdmin;

    KafkaProbe(KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }

    Map<String, Object> check() throws Exception {
        Map<String, Object> props = new HashMap<>(kafkaAdmin.getConfigurationProperties());
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, TIMEOUT_MS);
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, TIMEOUT_MS);

        Admin admin = Admin.create(props);
        try {
            DescribeClusterResult cluster = admin.describeCluster(new DescribeClusterOptions().timeoutMs(TIMEOUT_MS));
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("clusterId", cluster.clusterId().get(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            d.put("brokers", cluster.nodes().get(TIMEOUT_MS, TimeUnit.MILLISECONDS).size());

            Map<TopicPartition, OffsetAndMetadata> committed = admin.listConsumerGroupOffsets(GROUP)
                    .partitionsToOffsetAndMetadata().get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            Map<TopicPartition, OffsetAndMetadata> topicCommitted = committed.entrySet().stream()
                    .filter(e -> TOPIC.equals(e.getKey().topic()) && e.getValue() != null)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            long lag = 0;
            if (!topicCommitted.isEmpty()) {
                Map<TopicPartition, OffsetSpec> latest = topicCommitted.keySet().stream()
                        .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()));
                Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> ends =
                        admin.listOffsets(latest).all().get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
                for (var e : topicCommitted.entrySet()) {
                    lag += Math.max(0, ends.get(e.getKey()).offset() - e.getValue().offset());
                }
            }
            d.put("topic", TOPIC);
            d.put("consumerGroup", GROUP);
            d.put("assignedPartitions", topicCommitted.size());
            d.put("consumerLag", lag);
            return d;
        } finally {
            admin.close(Duration.ofMillis(500));
        }
    }
}
