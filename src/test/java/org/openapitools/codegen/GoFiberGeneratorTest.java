// Copyright (c) 2022, Samir Das <samiruor@gmail.com>

package org.openapitools.codegen;

import org.junit.Test;
import org.openapitools.codegen.config.CodegenConfigurator;

public class GoFiberGeneratorTest {
  @Test
  public void launchCodeGenerator() {
    final CodegenConfigurator configurator = new CodegenConfigurator()
            .setGeneratorName("go-fiber")
            .setInputSpec("./api.yaml")
            .setOutputDir("out/go-fiber")
            .setTemplatingEngineName("pebble")
            .setEnablePostProcessFile(true)
            .setPackageName("server")
            .addGlobalProperty("skipFormModel", "false");

    final ClientOptInput clientOptInput = configurator.toClientOptInput();
    DefaultGenerator generator = new DefaultGenerator();
    generator.opts(clientOptInput).generate();
  }
}
