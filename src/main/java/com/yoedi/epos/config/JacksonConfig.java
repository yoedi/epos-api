package com.yoedi.epos.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Configuration
public class JacksonConfig {

    public static final ZoneId STORE_ZONE = ZoneId.of("Asia/Jakarta");

    @Bean
    Jackson2ObjectMapperBuilderCustomizer eposJacksonCustomizer() {
        return builder -> builder
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .serializerByType(OffsetDateTime.class, new StoreZoneOffsetDateTimeSerializer());
    }

    /**
     * Writes every OffsetDateTime in the store time zone, e.g. "2026-09-16T14:32:10+07:00",
     * regardless of the offset the value was loaded with from the database.
     */
    static class StoreZoneOffsetDateTimeSerializer extends StdSerializer<OffsetDateTime> {

        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

        StoreZoneOffsetDateTimeSerializer() {
            super(OffsetDateTime.class);
        }

        @Override
        public void serialize(OffsetDateTime value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(value.atZoneSameInstant(STORE_ZONE).format(FORMATTER));
        }
    }
}
