package com.helpdesk.common.settings;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class HelpdeskSettingsTest {

    @Test
    void getInstanceAlwaysReturnsTheSameObject() {
        assertThat(HelpdeskSettings.getInstance()).isSameAs(HelpdeskSettings.getInstance());
    }

    @Test
    void constructorIsPrivate() throws Exception {
        assertThat(Modifier.isPrivate(HelpdeskSettings.class.getDeclaredConstructor().getModifiers())).isTrue();
    }

    @Test
    void concurrentCallsStillCreateOneObject() throws Exception {
        Set<HelpdeskSettings> seen = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newFixedThreadPool(10);
        for (int i = 0; i < 50; i++) {
            pool.submit(() -> seen.add(HelpdeskSettings.getInstance()));
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(seen).hasSize(1);
    }

    @Test
    void valuesMatchWhatTheFunctionsUsedBefore() {
        HelpdeskSettings s = HelpdeskSettings.getInstance();
        assertThat(s.getMaxUploadBytes()).isEqualTo(5L * 1024 * 1024);
        assertThat(s.getMaxAvatarBytes()).isEqualTo(2L * 1024 * 1024);
        assertThat(s.getTicketDefaultPageSize()).isEqualTo(20);
        assertThat(s.getTicketMaxPageSize()).isEqualTo(100);
        assertThat(s.getArticleMaxPageSize()).isEqualTo(50);
        assertThat(s.getMaxPage()).isEqualTo(10_000);
    }
}
