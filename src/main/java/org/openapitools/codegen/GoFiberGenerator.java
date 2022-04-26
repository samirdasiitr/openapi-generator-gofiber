/* Copyright (c) 2022, Samir Das <samiruor@gmail.com> */

package org.openapitools.codegen;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.openapitools.codegen.languages.AbstractGoCodegen;
import org.openapitools.codegen.languages.AbstractJavaCodegen;
import org.openapitools.codegen.templating.PebbleTemplateAdapter;
import org.openapitools.codegen.templating.mustache.SnakecaseLambda;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GoFiberGenerator extends AbstractGoCodegen {
  private final Logger LOGGER = LoggerFactory.getLogger(AbstractJavaCodegen.class);

  // source folder where to write the files
  protected String sourceFolder = "src";
  protected String apiVersion = "1.0.0";
  protected int serverPort = 8080;
  protected String projectName = "go-fiber";
  protected String apiPath = "go";
  protected Pattern columnPattern = Pattern.compile("column\\s*\\((.*)\\)",
          Pattern.CASE_INSENSITIVE|Pattern.MULTILINE);
  protected Pattern one2ManyPattern = Pattern.compile("onetomany\\s*\\((.*)\\)",
          Pattern.CASE_INSENSITIVE|Pattern.MULTILINE);
  protected Pattern one2onePattern = Pattern.compile("onetoone\\s*\\((.*)\\)",
          Pattern.CASE_INSENSITIVE|Pattern.MULTILINE);
  protected Pattern many2manyPattern = Pattern.compile("manytomany\\s*\\((.*)\\)",
          Pattern.CASE_INSENSITIVE|Pattern.MULTILINE);
  protected Pattern many2onePattern = Pattern.compile("manytoone\\s*\\((.*)\\)",
          Pattern.CASE_INSENSITIVE|Pattern.MULTILINE);

  /**
   * Configures the type of generator.
   *
   * @return the CodegenType for this generator
   * @see org.openapitools.codegen.CodegenType
   */
  public CodegenType getTag() {

    return CodegenType.SERVER;
  }

  /**
   * Configures a friendly name for the generator.  This will be used by the generator
   * to select the library with the -g flag.
   *
   * @return the friendly name for the generator
   */
  public String getName() {
    return "go-fiber";
  }

  /**
   * Returns human-friendly help for the generator.  Provide the consumer with help
   * tips, parameters here
   *
   * @return A string value for the help message
   */
  public String getHelp() {
    return "Generates a go-fiber framework.";
  }

  public GoFiberGenerator() {
    super();
    embeddedTemplateDir = templateDir = "go-fiber";
    modelTemplateFiles.put("model.bt", ".go");
    apiTemplateFiles.put("controller-api.bt", ".go");
    outputFolder = "generated-code/go";
    this.setReservedWordsLowerCase(Arrays.asList("string", "bool", "uint", "uint8", "uint16", "uint32", "uint64",
            "int", "int8", "int16", "int32", "int64", "float32", "float64", "complex64", "complex128", "rune", "byte",
            "uintptr", "break", "default", "func", "interface", "select", "case", "defer", "go", "map", "struct",
            "chan", "else", "goto", "package", "switch", "const", "fallthrough", "if", "range", "type", "continue",
            "for", "import", "return", "var", "error", "nil"));
    this.cliOptions.add(CliOption.newBoolean("enumClassPrefix", "Prefix enum with class name"));
  }

  @Override
  public Map<String, Object> postProcessAllModels(Map<String, Object> objs) {

    Map<String, Object> allObjs = super.postProcessAllModels(objs);
    Map<String, CodegenModel> allModels = getAllModels(objs);
    Map<String, Object> commonProperties = new HashMap<>();
    Set<String> globalImports = new HashSet<>();

    globalImports.add("gorm.io/gorm");

    for (String name : allModels.keySet()) {
      CodegenModel cm = allModels.get(name);
      List<Map> imports = (List<Map>) ((Map)allObjs.get(name)).get("imports");
      Set<String> cmImports = new HashSet<>();
      if (cm.isEnum) {
        cmImports.add("fmt");
      }

      for (CodegenProperty cp : cm.getVars()) {
        if (cp.getDataFormat() != null) {
          switch (cp.getDataFormat().toLowerCase()) {
            case "uuid":
              cp.setDatatype("uuid.UUID");
              cp.setDatatypeWithEnum("uuid.UUID");
              cmImports.add("github.com/google/uuid");
              break;
            case "binary":
            case "byte":
              cp.setDatatype("[]byte");
              cp.setDatatypeWithEnum("[]byte");
              break;
          }
        }

        // This is needed for stringify map (in String() go function)
        if (Objects.equals(cp.dataType, "map[string]interface{}")) {
          cp.isMap = true;
        }

        if (cp.getVendorExtensions().containsKey("x-sensitive")) {
          cp.getVendorExtensions().put("x_sensitive", true);
        }

        List<String> gormStr = new ArrayList<>();
      }

      cmImports.forEach(ent -> imports.add(ImmutableMap.of("import", ent)));

      if (!((boolean) cm.getVendorExtensions().getOrDefault("x-gorm", false))) {
        continue;
      }

      cm.getVendorExtensions().put("is_gorm_object", true);

      String tableName = (String) cm.getVendorExtensions().getOrDefault("x-gorm-table-name",
              org.openapitools.codegen.utils.StringUtils.underscore(cm.classname));

      cm.getVendorExtensions().put("gorm_table_name", tableName);

      for (CodegenProperty cp : cm.getVars()) {
        boolean primaryKey = (boolean) cp.getVendorExtensions().getOrDefault("x-gorm-primary-key", false);
        boolean autoGeneratePKey = (boolean) cp.getVendorExtensions().getOrDefault("x-gorm-column-auto-generate", false);
        boolean isIndex = (boolean) cp.getVendorExtensions().getOrDefault("x-gorm-index", false);
        String columnType = (String) cp.getVendorExtensions().getOrDefault("x-gorm-column-type", "");
        String columnAttributes = (String) cp.getVendorExtensions().getOrDefault("x-gorm-column-attrs", "");
        String foreignKey = (String) cp.getVendorExtensions().getOrDefault("x-gorm-foreign-key", "");
        String fkeyConstraints = (String) cp.getVendorExtensions().getOrDefault("x-gorm-foreign-key-constraints", "");
        boolean autoCreateTS = (boolean) cp.getVendorExtensions().getOrDefault("x-gorm-auto-create-timestamp", false);
        boolean autoUpdateTS = (boolean) cp.getVendorExtensions().getOrDefault("x-gorm-auto-update-timestamp", false);

        if (autoGeneratePKey && cp.dataFormat.equals("uuid"))
          cm.getVendorExtensions().put("auto_generate_pkey", cp.name);

        List<String> gormTags = new ArrayList<>();
        if (primaryKey)
          gormTags.add("primaryKey");

        String type = "type:";
        if (columnType.length() > 0)
          type += columnType;
        if (columnAttributes.length() > 0)
          if (columnType.length() > 0)
            type += "," + columnAttributes;
          else
            type += columnAttributes;

        if (!type.equals("type:"))
          gormTags.add(type);

        if (foreignKey.length() > 0) {
          gormTags.add("foreignKey:" + org.openapitools.codegen.utils.StringUtils.camelize(foreignKey));
        }

        if (fkeyConstraints.length() > 0) {
          gormTags.add("constraint:" + fkeyConstraints);
        }

        if (autoCreateTS) {
          gormTags.add("autoUpdateTime:milli");
        }

        if (autoUpdateTS) {
          gormTags.add("autoUpdateTime:milli");
        }

        if (isIndex) {
          gormTags.add("index");
        }

        if (gormTags.size() > 0) {
          LOGGER.info("Generated gorm tag => `gorm:\"{}\"`", String.join(";", gormTags));
          cp.getVendorExtensions().put("x_gorm_tag", String.join(";", gormTags));
        }

        cp.getVendorExtensions().put("gorm_datatype", cp.dataType);
        if (allModels.containsKey(cp.complexType)) {
          CodegenModel cpCM = allModels.get(cp.complexType);
          if ((boolean)cpCM.getVendorExtensions().getOrDefault("x-gorm", false)) {
            cp.getVendorExtensions().put("gorm_datatype", cp.dataType + "ORM");
          }
        }
      }

      //allObjs.remove(cm.getName());
    }

    commonProperties.put("imports", globalImports);

    return allObjs;
  }

  private void extractAssociationDetails(CodegenProperty cp, Matcher matcher) {

    String[] columnTokens = matcher.group(0).split(",");
    for (String str : columnTokens) {
      if (str.contains("mappedBy")) {
        cp.getVendorExtensions().put("gorm_association_refer", str.split("=")[1].trim());
      }
      if (str.contains("cascade")) {
        cp.getVendorExtensions().put("gorm_association_cascade", str.split("=")[1].trim());
      }
    }
  }

//  private String generateStringer(CodegenProperty cp) {
//    String str = "";
//    if (cp.isArray) {
//      str += String.format("str += `\"%s\" : [`\n\tfor ii, val := range in.%s {\n\t", cp.name, cp.name);
//      if (cp.items.isLong || cp.items.isInteger) {
//        str = String.format("\"%s\" : strconv.FormatInt(int64(in.%s), 10)", cp.name, cp.name);
//      } else if (cp.isFloat || cp.isDouble) {
//        str = String.format("\"%s\" : strconv.FormatFloat(float64(in.%s), 'f', -1, 64)", cp.name, cp.name);
//      } else if (cp.isBoolean) {
//        str = String.format("\"%s\" : strconv.FormatBool(in.%s)", cp.name, cp.name);
//      } else if (cp.isDateTime || cp.isDate) {
//        str = String.format("%sBytes, _ := in.%s.MarshalJSON()\n", cp.name);
//        str += String.format("\t\"%s\" : string(%sBytes)", cp.name, cp.name);
//      } else if (cp.isString) {
//        str = String.format("\"%s\" : in.%s", cp.name, cp.name);
//      } else if (!cp.isPrimitiveType) {
//        str = String.format("\"%s\" : in.%s.String()", cp.name, cp.name);
//      }
//    } else {
//      if (cp.isLong || cp.isInteger) {
//        str = String.format("\"%s\" : strconv.FormatInt(int64(in.%s), 10)", cp.name, cp.name);
//      } else if (cp.isFloat || cp.isDouble) {
//        str = String.format("\"%s\" : strconv.FormatFloat(float64(in.%s), 'f', -1, 64)", cp.name, cp.name);
//      } else if (cp.isBoolean) {
//        str = String.format("\"%s\" : strconv.FormatBool(in.%s)", cp.name, cp.name);
//      } else if (cp.isDateTime || cp.isDate) {
//        str = String.format("%sBytes, _ := in.%s.MarshalJSON()\n", cp.name);
//        str += String.format("\t\"%s\" : string(%sBytes)", cp.name, cp.name);
//      } else if (cp.isString) {
//        str = String.format("\"%s\" : in.%s", cp.name, cp.name);
//      } else if (!cp.isPrimitiveType) {
//        str = String.format("\"%s\" : in.%s.String()", cp.name, cp.name);
//      }
//    }

//    return str;
//  }

  private String convertPathToFiberPath(String path) {
    String[] tokens = path.split("/");
    for (int ii = 0; ii < tokens.length; ii++) {
      if (tokens[ii].startsWith("{")) {
        String temp = ":" + tokens[ii].substring(1, tokens[ii].length() - 1);
        tokens[ii] = temp;
      }
    }

    return String.join("/", tokens);
  }

  @Override
  public Map<String, Object> postProcessOperationsWithModels(Map<String, Object> objs, List<Object> allModels) {
    objs = super.postProcessOperationsWithModels(objs, allModels);
    Map<String, CodegenOperation> operations = (Map<String, CodegenOperation>) objs.get("operations");
    List<CodegenOperation> operation = (List<CodegenOperation>) operations.get("operation");

    for (CodegenOperation op : operation) {
      Set<String> imports = new HashSet<>();
      op.path = convertPathToFiberPath(op.path);

      for (CodegenParameter param : op.bodyParams) {
        param.vendorExtensions.put("errorVarName",
                param.paramName.substring(0, 1).toUpperCase() + param.paramName.substring(1));
      }

      for (CodegenParameter param : op.queryParams) {
        if (param.isString && param.dataFormat == "uuid") {
          imports.add("github.com/google/uuid");
        }
        param.vendorExtensions.put("varName",
                param.paramName.substring(0, 1).toUpperCase() + param.paramName.substring(1));
      }

      for (CodegenParameter param : op.formParams) {
        if (param.isFile) {
          imports.add("os");
        }
        param.vendorExtensions.put("varName",
                param.paramName.substring(0, 1).toUpperCase() + param.paramName.substring(1));
      }

      for (CodegenParameter param : op.pathParams) {
        param.vendorExtensions.put("varName",
                param.paramName.substring(0, 1).toUpperCase() + param.paramName.substring(1));
      }
      for (CodegenParameter param : op.allParams) {
        param.vendorExtensions.put("varName",
                param.paramName.substring(0, 1).toUpperCase() + param.paramName.substring(1));
      }

      op.imports.clear();
      op.imports.addAll(imports);
    }

    return objs;
  }

    @Override
  public void processOpts() {
      super.processOpts();

      if (this.additionalProperties.containsKey("packageName")) {
        this.setPackageName((String)this.additionalProperties.get("packageName"));
      } else {
        this.setPackageName("openapi");
        this.additionalProperties.put("packageName", this.packageName);
      }

      if (this.additionalProperties.containsKey("apiVersion")) {
        this.apiVersion = (String)this.additionalProperties.get("apiVersion");
      } else {
        this.additionalProperties.put("apiVersion", this.apiVersion);
      }

      if (this.additionalProperties.containsKey("serverPort")) {
        this.serverPort = Integer.parseInt((String)this.additionalProperties.get("serverPort"));
      } else {
        this.additionalProperties.put("serverPort", this.serverPort);
      }

      if (this.additionalProperties.containsKey("apiPath")) {
        this.apiPath = (String)this.additionalProperties.get("apiPath");
      } else {
        this.additionalProperties.put("apiPath", this.apiPath);
      }

      if (this.additionalProperties.containsKey("enumClassPrefix")) {
        this.setEnumClassPrefix(Boolean.parseBoolean(this.additionalProperties.get("enumClassPrefix").toString()));
        if (this.enumClassPrefix) {
          this.additionalProperties.put("enumClassPrefix", true);
        }
      }

      this.modelPackage = this.packageName;
      this.apiPackage = this.packageName;
      this.setTemplatingEngine(new PebbleTemplateAdapter());

      supportingFiles.add(new SupportingFile("router.bt", packageName, "router.go"));
      supportingFiles.add(new SupportingFile("util.bt", packageName, "util.go"));
      supportingFiles.add(new SupportingFile("log.bt", packageName, "logger.go"));
      supportingFiles.add(new SupportingFile("model-base.bt", packageName, "baseModel.go"));
      supportingFiles.add(new SupportingFile("gorm.bt", packageName, "gorm-models.go"));
//      supportingFiles.add(new SupportingFile("log_hook.mustache", "go", "log_hook.go"));
      //supportingFiles.add(new SupportingFile("model-orm.mustache", "go", "orm.go"));
  }

  @Override
  public void postProcessFile(File file, String fileType) {
    if (file == null) {
      return;
    }

    String goPostProcessFile = System.getenv("GO_POST_PROCESS_FILE");
    if (StringUtils.isEmpty(goPostProcessFile)) {
      return; // skip if GO_POST_PROCESS_FILE env variable is not defined
    }

    // only process the following type (or we can simply rely on the file extension to check if it's a Go file)
    Set<String> supportedFileType = new HashSet<>(
            Arrays.asList(
                    "supporting-file",
                    "supporting-mustache",
                    "model-test",
                    "model",
                    "api-test",
                    "api"));
    if (!supportedFileType.contains(fileType)) {
      return;
    }

    // only process files with go extension
    if ("go".equals(FilenameUtils.getExtension(file.toString()))) {
      // e.g. "gofmt -w yourcode.go"
      // e.g. "go fmt path/to/your/package"
      String command = goPostProcessFile + " " + file;
      try {
        Process p = Runtime.getRuntime().exec(command);
        int exitValue = p.waitFor();
        if (exitValue != 0) {
          LOGGER.error("Error running the command ({}). Exit code: {}", command, exitValue);
        } else {
          LOGGER.info("Successfully executed: {}", command);
        }
      } catch (InterruptedException | IOException e) {
        LOGGER.error("Error running the command ({}). Exception: {}", command, e.getMessage());
        // Restore interrupted state
        Thread.currentThread().interrupt();
      }
    }
  }
}