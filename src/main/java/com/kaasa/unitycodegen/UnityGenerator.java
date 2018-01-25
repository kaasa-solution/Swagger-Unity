package com.kaasa.unitycodegen;

import com.samskivert.mustache.Mustache;
import io.swagger.codegen.*;
import io.swagger.models.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

import static org.apache.commons.lang3.StringUtils.isEmpty;

public class UnityGenerator extends AbstractCSharpCodegen {
    @SuppressWarnings({ "hiding" })
    private static final Logger LOGGER = LoggerFactory.getLogger(UnityGenerator.class);

    protected String packageGuid = "{" + java.util.UUID.randomUUID().toString().toUpperCase() + "}";
    protected String clientPackage = "IO.Swagger.Client";
    protected String localVariablePrefix = "";
    protected String apiDocPath = "docs/";
    protected String modelDocPath = "docs/";

    protected Map<Character, String> regexModifiers;

    // By default, generated code is considered public
    protected boolean nonPublicApi = Boolean.FALSE;
    protected boolean hideGenerationTimestamp = Boolean.TRUE;

    public UnityGenerator() {
        super();
        supportsInheritance = true;
        modelTemplateFiles.put("model.mustache", ".cs");
        apiTemplateFiles.put("api.mustache", ".cs");

        modelDocTemplateFiles.put("model_doc.mustache", ".md");
        apiDocTemplateFiles.put("api_doc.mustache", ".md");

        cliOptions.clear();

        // CLI options
        addOption(CodegenConstants.PACKAGE_NAME, "C# package name (convention: Title.Case).", this.packageName);

        addOption(CodegenConstants.PACKAGE_VERSION, "C# package version.", this.packageVersion);

        addOption(CodegenConstants.SOURCE_FOLDER, CodegenConstants.SOURCE_FOLDER_DESC, sourceFolder);

        addOption(CodegenConstants.OPTIONAL_PROJECT_GUID, CodegenConstants.OPTIONAL_PROJECT_GUID_DESC, null);

        addOption(CodegenConstants.INTERFACE_PREFIX, CodegenConstants.INTERFACE_PREFIX_DESC, interfacePrefix);

        CliOption modelPropertyNaming = new CliOption(CodegenConstants.MODEL_PROPERTY_NAMING,
                CodegenConstants.MODEL_PROPERTY_NAMING_DESC);
        cliOptions.add(modelPropertyNaming.defaultValue("PascalCase"));

        // CLI Switches
        addSwitch(CodegenConstants.HIDE_GENERATION_TIMESTAMP, CodegenConstants.HIDE_GENERATION_TIMESTAMP_DESC,
                this.hideGenerationTimestamp);

        addSwitch(CodegenConstants.SORT_PARAMS_BY_REQUIRED_FLAG, CodegenConstants.SORT_PARAMS_BY_REQUIRED_FLAG_DESC,
                this.sortParamsByRequiredFlag);

        addSwitch(CodegenConstants.USE_DATETIME_OFFSET, CodegenConstants.USE_DATETIME_OFFSET_DESC,
                this.useDateTimeOffsetFlag);

        addSwitch(CodegenConstants.USE_COLLECTION, CodegenConstants.USE_COLLECTION_DESC, this.useCollection);

        addSwitch(CodegenConstants.RETURN_ICOLLECTION, CodegenConstants.RETURN_ICOLLECTION_DESC,
                this.returnICollection);

        addSwitch(CodegenConstants.OPTIONAL_METHOD_ARGUMENT,
                "C# Optional method argument, e.g. void square(int x=10) (.net 4.0+ only).",
                this.optionalMethodArgumentFlag);

        addSwitch(CodegenConstants.OPTIONAL_ASSEMBLY_INFO, CodegenConstants.OPTIONAL_ASSEMBLY_INFO_DESC,
                this.optionalAssemblyInfoFlag);

        addSwitch(CodegenConstants.OPTIONAL_PROJECT_FILE, CodegenConstants.OPTIONAL_PROJECT_FILE_DESC,
                this.optionalProjectFileFlag);

        addSwitch(CodegenConstants.OPTIONAL_EMIT_DEFAULT_VALUES, CodegenConstants.OPTIONAL_EMIT_DEFAULT_VALUES_DESC,
                this.optionalEmitDefaultValue);

        // NOTE: This will reduce visibility of all public members in templates. Users can use InternalsVisibleTo
        // https://msdn.microsoft.com/en-us/library/system.runtime.compilerservices.internalsvisibletoattribute(v=vs.110).aspx
        // to expose to shared code if the generated code is not embedded into another project. Otherwise, users of codegen
        // should rely on default public visibility.
        addSwitch(CodegenConstants.NON_PUBLIC_API, CodegenConstants.NON_PUBLIC_API_DESC, this.nonPublicApi);

        addSwitch(CodegenConstants.ALLOW_UNICODE_IDENTIFIERS, CodegenConstants.ALLOW_UNICODE_IDENTIFIERS_DESC,
                this.allowUnicodeIdentifiers);

        addSwitch(CodegenConstants.NETCORE_PROJECT_FILE, CodegenConstants.NETCORE_PROJECT_FILE_DESC,
                this.netCoreProjectFileFlag);

        regexModifiers = new HashMap<Character, String>();
        regexModifiers.put('i', "IgnoreCase");
        regexModifiers.put('m', "Multiline");
        regexModifiers.put('s', "Singleline");
        regexModifiers.put('x', "IgnorePatternWhitespace");
    }

    @Override
    public void processOpts() {
        super.processOpts();

        /*
         * NOTE: When supporting boolean additionalProperties, you should read the value and write it back as a boolean.
         * This avoids oddities where additionalProperties contains "false" rather than false, which will cause the
         * templating engine to behave unexpectedly.
         *
         * Use the pattern:
         *     if (additionalProperties.containsKey(prop)) convertPropertyToBooleanAndWriteBack(prop);
         */

        if (additionalProperties.containsKey(CodegenConstants.MODEL_PROPERTY_NAMING)) {
            setModelPropertyNaming((String) additionalProperties.get(CodegenConstants.MODEL_PROPERTY_NAMING));
        }

        // default HIDE_GENERATION_TIMESTAMP to true
        if (additionalProperties.containsKey(CodegenConstants.HIDE_GENERATION_TIMESTAMP)) {
            setHideGenerationTimestamp(
                    convertPropertyToBooleanAndWriteBack(CodegenConstants.HIDE_GENERATION_TIMESTAMP));
        } else {
            additionalProperties.put(CodegenConstants.HIDE_GENERATION_TIMESTAMP, hideGenerationTimestamp);
        }

        if (isEmpty(apiPackage)) {
            setApiPackage("Api");
        }
        if (isEmpty(modelPackage)) {
            setModelPackage("Model");
        }
        clientPackage = "Client";

        Boolean excludeTests = false;
        if (additionalProperties.containsKey(CodegenConstants.EXCLUDE_TESTS)) {
            excludeTests = convertPropertyToBooleanAndWriteBack(CodegenConstants.EXCLUDE_TESTS);
        }

        additionalProperties.put(CodegenConstants.API_PACKAGE, apiPackage);
        additionalProperties.put(CodegenConstants.MODEL_PACKAGE, modelPackage);
        additionalProperties.put("clientPackage", clientPackage);

        additionalProperties.put(CodegenConstants.EXCLUDE_TESTS, excludeTests);

        // TODO: either remove this and update templates to match the "optionalEmitDefaultValues" property, or rename that property.
        additionalProperties.put("emitDefaultValue", optionalEmitDefaultValue);

        if (additionalProperties.containsKey(CodegenConstants.OPTIONAL_PROJECT_FILE)) {
            setOptionalProjectFileFlag(convertPropertyToBooleanAndWriteBack(CodegenConstants.OPTIONAL_PROJECT_FILE));
        } else {
            additionalProperties.put(CodegenConstants.OPTIONAL_PROJECT_FILE, optionalProjectFileFlag);
        }

        if (additionalProperties.containsKey(CodegenConstants.OPTIONAL_PROJECT_GUID)) {
            setPackageGuid((String) additionalProperties.get(CodegenConstants.OPTIONAL_PROJECT_GUID));
        } else {
            additionalProperties.put(CodegenConstants.OPTIONAL_PROJECT_GUID, packageGuid);
        }

        if (additionalProperties.containsKey(CodegenConstants.OPTIONAL_METHOD_ARGUMENT)) {
            setOptionalMethodArgumentFlag(
                    convertPropertyToBooleanAndWriteBack(CodegenConstants.OPTIONAL_METHOD_ARGUMENT));
        } else {
            additionalProperties.put(CodegenConstants.OPTIONAL_METHOD_ARGUMENT, optionalMethodArgumentFlag);
        }

        if (additionalProperties.containsKey(CodegenConstants.OPTIONAL_ASSEMBLY_INFO)) {
            setOptionalAssemblyInfoFlag(convertPropertyToBooleanAndWriteBack(CodegenConstants.OPTIONAL_ASSEMBLY_INFO));
        } else {
            additionalProperties.put(CodegenConstants.OPTIONAL_ASSEMBLY_INFO, optionalAssemblyInfoFlag);
        }

        if (additionalProperties.containsKey(CodegenConstants.NON_PUBLIC_API)) {
            setNonPublicApi(convertPropertyToBooleanAndWriteBack(CodegenConstants.NON_PUBLIC_API));
        } else {
            additionalProperties.put(CodegenConstants.NON_PUBLIC_API, isNonPublicApi());
        }

        final String testPackageName = testPackageName();
        String packageFolder = sourceFolder + File.separator + packageName;
        String clientPackageDir = packageFolder + File.separator + clientPackage;
        String testPackageFolder = testFolder + File.separator + testPackageName;

        additionalProperties.put("testPackageName", testPackageName);

        //Compute the relative path to the bin directory where the external assemblies live
        //This is necessary to properly generate the project file
        int packageDepth = packageFolder.length() - packageFolder.replace(java.io.File.separator, "").length();
        String binRelativePath = "..\\";
        for (int i = 0; i < packageDepth; i = i + 1)
            binRelativePath += "..\\";
        binRelativePath += "vendor";
        additionalProperties.put("binRelativePath", binRelativePath);

        supportingFiles.add(new SupportingFile("IApiAccessor.mustache", clientPackageDir, "IApiAccessor.cs"));
        supportingFiles.add(new SupportingFile("Configuration.mustache", clientPackageDir, "Configuration.cs"));
        supportingFiles.add(new SupportingFile("ApiClient.mustache", clientPackageDir, "ApiClient.cs"));
        supportingFiles.add(new SupportingFile("ApiException.mustache", clientPackageDir, "ApiException.cs"));
        supportingFiles.add(new SupportingFile("ApiResponse.mustache", clientPackageDir, "ApiResponse.cs"));
        supportingFiles.add(new SupportingFile("ExceptionFactory.mustache", clientPackageDir, "ExceptionFactory.cs"));
        supportingFiles
                .add(new SupportingFile("SwaggerDateConverter.mustache", clientPackageDir, "SwaggerDateConverter.cs"));

        if (Boolean.FALSE.equals(this.netCoreProjectFileFlag)) {
            supportingFiles.add(new SupportingFile("compile.mustache", "", "build.bat"));
            supportingFiles.add(new SupportingFile("compile-mono.sh.mustache", "", "build.sh"));

            // copy package.config to nuget's standard location for project-level installs
            supportingFiles.add(
                    new SupportingFile("packages.config.mustache", packageFolder + File.separator, "packages.config"));
            // .travis.yml for travis-ci.org CI
            supportingFiles.add(new SupportingFile("travis.mustache", "", ".travis.yml"));
        }

        supportingFiles.add(
                new SupportingFile("IReadableConfiguration.mustache", clientPackageDir, "IReadableConfiguration.cs"));
        supportingFiles
                .add(new SupportingFile("GlobalConfiguration.mustache", clientPackageDir, "GlobalConfiguration.cs"));

        // Only write out test related files if excludeTests is unset or explicitly set to false (see start of this method)
        if (Boolean.FALSE.equals(excludeTests)) {
            // shell script to run the nunit test
            supportingFiles.add(new SupportingFile("mono_nunit_test.mustache", "", "mono_nunit_test.sh"));

            modelTestTemplateFiles.put("model_test.mustache", ".cs");
            apiTestTemplateFiles.put("api_test.mustache", ".cs");

            if (Boolean.FALSE.equals(this.netCoreProjectFileFlag)) {
                supportingFiles.add(new SupportingFile("packages_test.config.mustache",
                        testPackageFolder + File.separator, "packages.config"));
            }
        }

        supportingFiles.add(new SupportingFile("README.mustache", "", "README.md"));
        supportingFiles.add(new SupportingFile("git_push.sh.mustache", "", "git_push.sh"));
        supportingFiles.add(new SupportingFile("gitignore.mustache", "", ".gitignore"));

        if (optionalAssemblyInfoFlag && Boolean.FALSE.equals(this.netCoreProjectFileFlag)) {
            supportingFiles.add(new SupportingFile("AssemblyInfo.mustache",
                    packageFolder + File.separator + "Properties", "AssemblyInfo.cs"));
        }
        if (optionalProjectFileFlag) {
            supportingFiles.add(new SupportingFile("Solution.mustache", "", packageName + ".sln"));

            if (Boolean.TRUE.equals(this.netCoreProjectFileFlag)) {
                supportingFiles
                        .add(new SupportingFile("netcore_project.mustache", packageFolder, packageName + ".csproj"));
            } else {
                supportingFiles.add(new SupportingFile("Project.mustache", packageFolder, packageName + ".csproj"));
                supportingFiles.add(new SupportingFile("nuspec.mustache", packageFolder, packageName + ".nuspec"));
            }

            if (Boolean.FALSE.equals(excludeTests)) {
                // NOTE: This exists here rather than previous excludeTests block because the test project is considered an optional project file.
                if (Boolean.TRUE.equals(this.netCoreProjectFileFlag)) {
                    supportingFiles.add(new SupportingFile("netcore_testproject.mustache", testPackageFolder,
                            testPackageName + ".csproj"));
                } else {
                    supportingFiles.add(
                            new SupportingFile("TestProject.mustache", testPackageFolder, testPackageName + ".csproj"));
                }
            }
        }

        additionalProperties.put("apiDocPath", apiDocPath);
        additionalProperties.put("modelDocPath", modelDocPath);
    }

    public void setModelPropertyNaming(String naming) {
        if ("original".equals(naming) || "camelCase".equals(naming) || "PascalCase".equals(naming)
                || "snake_case".equals(naming)) {
            this.modelPropertyNaming = naming;
        } else {
            throw new IllegalArgumentException("Invalid model property naming '" + naming
                    + "'. Must be 'original', 'camelCase', " + "'PascalCase' or 'snake_case'");
        }
    }

    public String getModelPropertyNaming() {
        return this.modelPropertyNaming;
    }

    @Override
    public Map<String, Object> postProcessOperations(Map<String, Object> objs) {
        super.postProcessOperations(objs);
        if (objs != null) {
            Map<String, Object> operations = (Map<String, Object>) objs.get("operations");
            if (operations != null) {
                List<CodegenOperation> ops = (List<CodegenOperation>) operations.get("operation");
                for (CodegenOperation operation : ops) {
                    if (operation.returnType != null) {
                        operation.returnContainer = operation.returnType;
                        if (this.returnICollection && (operation.returnType.startsWith("List")
                                || operation.returnType.startsWith("Collection"))) {
                            // NOTE: ICollection works for both List<T> and Collection<T>
                            int genericStart = operation.returnType.indexOf("<");
                            if (genericStart > 0) {
                                operation.returnType = "ICollection" + operation.returnType.substring(genericStart);
                            }
                        }
                    }
                }
            }
        }

        return objs;
    }

    @Override
    public CodegenType getTag() {
        return CodegenType.CLIENT;
    }

    @Override
    public String getName() {
        return "unity";
    }

    @Override
    public String getHelp() {
        return "Generates a Unity client library.";
    }

    public void setOptionalAssemblyInfoFlag(boolean flag) {
        this.optionalAssemblyInfoFlag = flag;
    }

    @Override
    public CodegenModel fromModel(String name, Model model, Map<String, Model> allDefinitions) {
        CodegenModel codegenModel = super.fromModel(name, model, allDefinitions);
        if (allDefinitions != null && codegenModel != null && codegenModel.parent != null) {
            final Model parentModel = allDefinitions.get(toModelName(codegenModel.parent));
            if (parentModel != null) {
                final CodegenModel parentCodegenModel = super.fromModel(codegenModel.parent, parentModel);
                if (codegenModel.hasEnums) {
                    codegenModel = this.reconcileInlineEnums(codegenModel, parentCodegenModel);
                }

                Map<String, CodegenProperty> propertyHash = new HashMap<>(codegenModel.vars.size());
                for (final CodegenProperty property : codegenModel.vars) {
                    propertyHash.put(property.name, property);
                }

                for (final CodegenProperty property : codegenModel.readWriteVars) {
                    if (property.defaultValue == null && property.baseName.equals(parentCodegenModel.discriminator)) {
                        property.defaultValue = "\"" + name + "\"";
                    }
                }

                CodegenProperty last = null;
                for (final CodegenProperty property : parentCodegenModel.vars) {
                    // helper list of parentVars simplifies templating
                    if (!propertyHash.containsKey(property.name)) {
                        final CodegenProperty parentVar = property.clone();
                        parentVar.isInherited = true;
                        parentVar.hasMore = true;
                        last = parentVar;
                        LOGGER.info("adding parent variable {}", property.name);
                        codegenModel.parentVars.add(parentVar);
                    }
                }

                if (last != null) {
                    last.hasMore = false;
                }
            }
        }

        // Cleanup possible duplicates. Currently, readWriteVars can contain the same property twice. May or may not be isolated to C#.
        if (codegenModel != null && codegenModel.readWriteVars != null && codegenModel.readWriteVars.size() > 1) {
            int length = codegenModel.readWriteVars.size() - 1;
            for (int i = length; i > (length / 2); i--) {
                final CodegenProperty codegenProperty = codegenModel.readWriteVars.get(i);
                // If the property at current index is found earlier in the list, remove this last instance.
                if (codegenModel.readWriteVars.indexOf(codegenProperty) < i) {
                    codegenModel.readWriteVars.remove(i);
                }
            }
        }

        return codegenModel;
    }

    public void setOptionalProjectFileFlag(boolean flag) {
        this.optionalProjectFileFlag = flag;
    }

    public void setPackageGuid(String packageGuid) {
        this.packageGuid = packageGuid;
    }

    @Override
    public void postProcessParameter(CodegenParameter parameter) {
        postProcessPattern(parameter.pattern, parameter.vendorExtensions);
        super.postProcessParameter(parameter);
    }

    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        postProcessPattern(property.pattern, property.vendorExtensions);
        super.postProcessModelProperty(model, property);
    }

    /*
    * The swagger pattern spec follows the Perl convention and style of modifiers. .NET
    * does not support this syntax directly so we need to convert the pattern to a .NET compatible
    * format and apply modifiers in a compatible way.
    * See https://msdn.microsoft.com/en-us/library/yd1hzczs(v=vs.110).aspx for .NET options.
    * See https://github.com/swagger-api/swagger-codegen/pull/2794 for Python's initial implementation from which this is copied.
    */
    public void postProcessPattern(String pattern, Map<String, Object> vendorExtensions) {
        if (pattern != null) {
            int i = pattern.lastIndexOf('/');

            //Must follow Perl /pattern/modifiers convention
            if (pattern.charAt(0) != '/' || i < 2) {
                throw new IllegalArgumentException("Pattern must follow the Perl " + "/pattern/modifiers convention. "
                        + pattern + " is not valid.");
            }

            String regex = pattern.substring(1, i).replace("'", "\'");
            List<String> modifiers = new ArrayList<String>();

            // perl requires an explicit modifier to be culture specific and .NET is the reverse.
            modifiers.add("CultureInvariant");

            for (char c : pattern.substring(i).toCharArray()) {
                if (regexModifiers.containsKey(c)) {
                    String modifier = regexModifiers.get(c);
                    modifiers.add(modifier);
                } else if (c == 'l') {
                    modifiers.remove("CultureInvariant");
                }
            }

            vendorExtensions.put("x-regex", regex);
            vendorExtensions.put("x-modifiers", modifiers);
        }
    }

    private CodegenModel reconcileInlineEnums(CodegenModel codegenModel, CodegenModel parentCodegenModel) {
        // This generator uses inline classes to define enums, which breaks when
        // dealing with models that have subTypes. To clean this up, we will analyze
        // the parent and child models, look for enums that match, and remove
        // them from the child models and leave them in the parent.
        // Because the child models extend the parents, the enums will be available via the parent.

        // Only bother with reconciliation if the parent model has enums.
        if (parentCodegenModel.hasEnums) {

            // Get the properties for the parent and child models
            final List<CodegenProperty> parentModelCodegenProperties = parentCodegenModel.vars;
            List<CodegenProperty> codegenProperties = codegenModel.vars;

            // Iterate over all of the parent model properties
            boolean removedChildEnum = false;
            for (CodegenProperty parentModelCodegenPropery : parentModelCodegenProperties) {
                // Look for enums
                if (parentModelCodegenPropery.isEnum) {
                    // Now that we have found an enum in the parent class,
                    // and search the child class for the same enum.
                    Iterator<CodegenProperty> iterator = codegenProperties.iterator();
                    while (iterator.hasNext()) {
                        CodegenProperty codegenProperty = iterator.next();
                        if (codegenProperty.isEnum && codegenProperty.equals(parentModelCodegenPropery)) {
                            // We found an enum in the child class that is
                            // a duplicate of the one in the parent, so remove it.
                            iterator.remove();
                            removedChildEnum = true;
                        }
                    }
                }
            }

            if (removedChildEnum) {
                // If we removed an entry from this model's vars, we need to ensure hasMore is updated
                int count = 0, numVars = codegenProperties.size();
                for (CodegenProperty codegenProperty : codegenProperties) {
                    count += 1;
                    codegenProperty.hasMore = count < numVars;
                }
                codegenModel.vars = codegenProperties;
            }
        }

        return codegenModel;
    }

    @Override
    public String toEnumVarName(String value, String datatype) {
        if (value.length() == 0) {
            return "Empty";
        }

        // for symbol, e.g. $, #
        if (getSymbolName(value) != null) {
            return camelize(getSymbolName(value));
        }

        // number
        if (datatype.startsWith("int") || datatype.startsWith("long") || datatype.startsWith("double")
                || datatype.startsWith("float")) {
            String varName = "NUMBER_" + value;
            varName = varName.replaceAll("-", "MINUS_");
            varName = varName.replaceAll("\\+", "PLUS_");
            varName = varName.replaceAll("\\.", "_DOT_");
            return varName;
        }

        // string
        String var = value.replaceAll("_", " ");
        //var = WordUtils.capitalizeFully(var);
        var = camelize(var);
        var = var.replaceAll("\\W+", "");

        if (var.matches("\\d.*")) {
            return "_" + var;
        } else {
            return var;
        }
    }

    @Override
    public String toVarName(String name) {
        // sanitize name
        name = sanitizeName(name);

        // if it's all uppper case, do nothing
        if (name.matches("^[A-Z_]*$")) {
            return name;
        }

        name = getNameUsingModelPropertyNaming(name);

        // for reserved word or word starting with number, append _
        if (isReservedWord(name) || name.matches("^\\d.*")) {
            name = escapeReservedWord(name);
        }

        return name;
    }

    public String getNameUsingModelPropertyNaming(String name) {
        switch (CodegenConstants.MODEL_PROPERTY_NAMING_TYPE.valueOf(getModelPropertyNaming())) {
        case original:
            return name;
        case camelCase:
            return camelize(name, true);
        case PascalCase:
            return camelize(name);
        case snake_case:
            return underscore(name);
        default:
            throw new IllegalArgumentException("Invalid model property naming '" + name
                    + "'. Must be 'original', 'camelCase', " + "'PascalCase' or 'snake_case'");
        }
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public void setPackageVersion(String packageVersion) {
        this.packageVersion = packageVersion;
    }

    public void setHideGenerationTimestamp(boolean hideGenerationTimestamp) {
        this.hideGenerationTimestamp = hideGenerationTimestamp;
    }

    public boolean isNonPublicApi() {
        return nonPublicApi;
    }

    public void setNonPublicApi(final boolean nonPublicApi) {
        this.nonPublicApi = nonPublicApi;
    }

    @Override
    public String toModelDocFilename(String name) {
        return toModelFilename(name);
    }

    @Override
    public String apiDocFileFolder() {
        return (outputFolder + "/" + apiDocPath).replace('/', File.separatorChar);
    }

    @Override
    public String modelDocFileFolder() {
        return (outputFolder + "/" + modelDocPath).replace('/', File.separatorChar);
    }

    @Override
    public String apiTestFileFolder() {
        return outputFolder + File.separator + testFolder + File.separator + testPackageName() + File.separator
                + apiPackage();
    }

    @Override
    public String modelTestFileFolder() {
        return outputFolder + File.separator + testFolder + File.separator + testPackageName() + File.separator
                + modelPackage();
    }

    @Override
    public Mustache.Compiler processCompiler(Mustache.Compiler compiler) {
        // To avoid unexpected behaviors when options are passed programmatically such as { "supportsAsync": "" }
        return super.processCompiler(compiler).emptyStringIsFalse(true);
    }
}
