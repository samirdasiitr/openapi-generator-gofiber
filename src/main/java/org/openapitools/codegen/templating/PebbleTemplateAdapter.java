package org.openapitools.codegen.templating;

import com.mitchellbosecke.pebble.PebbleEngine;
import com.mitchellbosecke.pebble.loader.ClasspathLoader;
import com.mitchellbosecke.pebble.loader.Loader;
import org.openapitools.codegen.api.AbstractTemplatingEngineAdapter;
import org.openapitools.codegen.api.TemplatingExecutor;
import org.openapitools.codegen.languages.AbstractJavaCodegen;
import org.openapitools.codegen.templating.TemplateNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

public class PebbleTemplateAdapter extends AbstractTemplatingEngineAdapter {
    private final Logger LOGGER = LoggerFactory.getLogger(AbstractJavaCodegen.class);
    private final String[] extensions = new String[]{"bt"};
    private final String[] canCompileFromExtensions = new String[]{".bt"};

    // initialize the template compilation engine
    private Loader<?> loader = new ClasspathLoader();
    private final PebbleEngine engine = new PebbleEngine.Builder()
            .cacheActive(false)
            .loader(loader)
            .build();

    public PebbleTemplateAdapter() {
    }

    @Override
    public String getIdentifier() {
        return "pebble";
    }

    @Override
    public String[] getFileExtensions() {
        return this.extensions;
    }

    @Override
    public String compileTemplate(final TemplatingExecutor executor,
            Map<String, Object> bundle, String templateFile) throws IOException {
        String modifiedTemplate = this.getModifiedFileLocation(templateFile)[0];
        Path filepath = executor.getFullTemplatePath(modifiedTemplate);
        StringWriter writer = new StringWriter();
        engine.getTemplate(filepath.toString()).evaluate(writer, bundle);
        return writer.toString();
    }

    @Override
    public boolean handlesFile(String filename) {
        return Arrays.stream(this.canCompileFromExtensions).anyMatch((suffix) -> {
            return !suffix.equalsIgnoreCase(filename) && filename.endsWith(suffix);
        });
    }
}
