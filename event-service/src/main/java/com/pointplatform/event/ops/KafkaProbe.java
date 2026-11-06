package com.pointplatform.event.ops;

import com.pointplatform.event.reward.RewardPublisher;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Kafka(MSK) 접속 확인 + 보상 토픽 상태.
 * publishedOffsetTotal = 토픽 파티션별 끝 offset 합 → 당첨/출석 시 숫자가 늘어나면 발행이 실제로 된 것.
 * 토픽이 아직 없으면(로컬 첫 실행: 첫 발행 시 자동 생성) topicExists=false 로만 표시한다.
 */
@Component
class KafkaProbe {

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

            String topic = RewardPublisher.TOPIC;
            d.put("topic", topic);
            boolean exists = admin.listTopics().names().get(TIMEOUT_MS, TimeUnit.MILLISECONDS).contains(topic);
            d.put("topicExists", exists);
            if (exists) {
                TopicDescription description = admin.describeTopics(List.of(topic))
                        .allTopicNames().get(TIMEOUT_MS, TimeUnit.MILLISECONDS).get(topic);
                Map<TopicPartition, OffsetSpec> latest = description.partitions().stream()
                        .collect(Collectors.toMap(p -> new TopicPartition(topic, p.partition()), p -> OffsetSpec.latest()));
                long total = admin.listOffsets(latest).all().get(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                        .values().stream().mapToLong(info -> info.offset()).sum();
                d.put("partitions", description.partitions().size());
                d.put("publishedOffsetTotal", total);
            }
            return d;
        } finally {
            admin.close(Duration.ofMillis(500));
        }
    }
}
