package com.vw.eacontext.api;

import java.io.InputStream;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.vw.eacontext.ingestion.JsonEaDataParser;
import com.vw.eacontext.model.CanonicalModel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * On startup, auto-loads the bundled sample dataset into the
 * {@link SessionModelStore} so the API is usable without an upload (MVP).
 *
 * <p>Enabled by default; disable with {@code ea.sample.autoload=false}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ea.sample.autoload", havingValue = "true", matchIfMissing = true)
public class SampleDataInitializer implements ApplicationRunner {

    private static final String SAMPLE = "sample_ea_dataset.json";

    private final JsonEaDataParser jsonParser;
    private final SessionModelStore store;

    @Override
    public void run(ApplicationArguments args) {
        try (InputStream in = new ClassPathResource(SAMPLE).getInputStream()) {
            CanonicalModel model = jsonParser.parse(in);
            store.load(model);
            log.info("Auto-loaded bundled sample dataset '{}'", SAMPLE);
        } catch (Exception e) {
            // Non-fatal: the app still starts; users can upload their own dataset.
            log.warn("Could not auto-load sample dataset '{}': {}", SAMPLE, e.toString());
        }
    }
}
