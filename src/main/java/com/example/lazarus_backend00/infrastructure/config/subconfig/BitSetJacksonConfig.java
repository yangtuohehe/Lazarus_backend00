package com.example.lazarus_backend00.infrastructure.config.subconfig;


import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * 拦截器：将系统中所有的 BitSet 与 JSON 数组进行无缝转换
 * 保护领域实体的纯洁性，无需在 TSState 中写任何注解
 */
@Configuration
public class BitSetJacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer bitSetCustomizer() {
        return builder -> {
            // 1. 发送时：将 BitSet 拦截并转为 JSON 长整型数组 [ -1, -1 ... ]
            builder.serializerByType(BitSet.class, new JsonSerializer<BitSet>() {
                @Override
                public void serialize(BitSet value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                    if (value == null) {
                        gen.writeNull();
                        return;
                    }
                    long[] longs = value.toLongArray();
                    gen.writeStartArray();
                    for (long l : longs) {
                        gen.writeNumber(l);
                    }
                    gen.writeEndArray();
                }
            });

            // 2. 接收时：将收到的 JSON 数组还原回内存的高性能 BitSet
            builder.deserializerByType(BitSet.class, new JsonDeserializer<BitSet>() {
                @Override
                public BitSet deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                    if (p.isExpectedStartArrayToken()) {
                        List<Long> longs = new ArrayList<>();
                        while (p.nextToken() != JsonToken.END_ARRAY) {
                            longs.add(p.getLongValue());
                        }
                        long[] longArray = longs.stream().mapToLong(l -> l).toArray();
                        return BitSet.valueOf(longArray);
                    }
                    return new BitSet();
                }
            });
        };
    }
}