package viaduct.tenant.codegen.dsl

import getEscapedFieldName
import java.io.File
import viaduct.codegen.km.kotlinTypeString
import viaduct.codegen.st.STContents
import viaduct.codegen.st.stTemplate
import viaduct.codegen.utils.JavaName
import viaduct.graphql.schema.ViaductSchema
import viaduct.tenant.codegen.bytecode.config.BaseTypeMapper
import viaduct.tenant.codegen.bytecode.config.kmType

class DslFilesBuilder(
    private val pkg: String,
    private val outputDir: File,
    private val baseTypeMapper: BaseTypeMapper
) {
    fun generate(schema: ViaductSchema) {
        outputDir.mkdirs()
        val objectTypesNeedingBuilders = mutableSetOf<String>()

        schema.types["Query"]?.let { queryType ->
            if (queryType is ViaductSchema.Object) {
                generateQueryDsl(queryType)
                collectNeededBuilders(queryType, objectTypesNeedingBuilders, schema)
            }
        }

        schema.types["Mutation"]?.let { mutationType ->
            if (mutationType is ViaductSchema.Object) {
                generateMutationDsl(mutationType)
                collectNeededBuilders(mutationType, objectTypesNeedingBuilders, schema)
            }
        }

        schema.types["Subscription"]?.let { subscriptionType ->
            if (subscriptionType is ViaductSchema.Object) {
                generateSubscriptionDsl(subscriptionType)
                collectNeededBuilders(subscriptionType, objectTypesNeedingBuilders, schema)
            }
        }

        for (typeName in objectTypesNeedingBuilders) {
            val typeDef = schema.types[typeName]
            if (typeDef is ViaductSchema.Object && !isRootType(typeName)) {
                generateObjectDsl(typeDef)
            }
        }

        generateNodeInterfaceSupport(schema, objectTypesNeedingBuilders)
    }

    private fun generateQueryDsl(queryType: ViaductSchema.Object) {
        val dst = File(outputDir, "QueryDsl.kt")
        queryDslGen(pkg, queryType, baseTypeMapper).write(dst)
    }

    private fun generateMutationDsl(mutationType: ViaductSchema.Object) {
        val dst = File(outputDir, "MutationDsl.kt")
        mutationDslGen(pkg, mutationType, baseTypeMapper).write(dst)
    }

    private fun generateSubscriptionDsl(subscriptionType: ViaductSchema.Object) {
        val dst = File(outputDir, "SubscriptionDsl.kt")
        subscriptionDslGen(pkg, subscriptionType, baseTypeMapper).write(dst)
    }

    private fun generateObjectDsl(objectType: ViaductSchema.Object) {
        val dst = File(outputDir, "${objectType.name}DslBuilder.kt")
        objectDslGen(pkg, objectType, baseTypeMapper).write(dst)
    }

    private fun generateNodeInterfaceSupport(
        schema: ViaductSchema,
        objectTypesNeedingBuilders: MutableSet<String>
    ) {
        for ((typeName, typeDef) in schema.types) {
            if (typeDef is ViaductSchema.Interface) {
                val implementingTypes = schema.types.values
                    .filterIsInstance<ViaductSchema.Object>()
                    .filter { obj -> obj.supers.any { it.name == typeName } }

                if (implementingTypes.isNotEmpty()) {
                    val dst = File(outputDir, "${typeName}DslBuilder.kt")
                    nodeInterfaceDslGen(pkg, typeDef, implementingTypes).write(dst)
                    implementingTypes.forEach { objectTypesNeedingBuilders.add(it.name) }
                }
            }
        }
    }

    private fun collectNeededBuilders(
        typeDef: ViaductSchema.Object,
        collectors: MutableSet<String>,
        schema: ViaductSchema
    ) {
        for (field in typeDef.fields) {
            when (val returnType = field.type.baseTypeDef) {
                is ViaductSchema.Object -> {
                    if (!isRootType(returnType.name) && collectors.add(returnType.name)) {
                        collectNeededBuilders(returnType, collectors, schema)
                    }
                }
                is ViaductSchema.Interface, is ViaductSchema.Union -> collectors.add(returnType.name)
            }
        }
    }

    private fun isRootType(name: String) = name in setOf("Query", "Mutation", "Subscription")
}

fun subscriptionDslGen(pkg: String, subscriptionDef: ViaductSchema.Object, baseTypeMapper: BaseTypeMapper) =
    STContents(subscriptionDslSTGroup, SubscriptionDslModelImpl(pkg, subscriptionDef, baseTypeMapper))

private interface SubscriptionDslModel {
    val pkg: String
    val scalarFields: List<SubscriptionFieldModel>
    val complexFields: List<SubscriptionFieldModel>

    class SubscriptionFieldModel(pkg: String, fieldDef: ViaductSchema.Field, baseTypeMapper: BaseTypeMapper) {
        val escapedName = getEscapedFieldName(fieldDef.name)
        val fieldName = fieldDef.name
        val parameters = fieldDef.args.map { ParameterModel(pkg, it, baseTypeMapper) }
        val returnTypeDef = fieldDef.type.baseTypeDef
        val needsSelection = returnTypeDef is ViaductSchema.Object || returnTypeDef is ViaductSchema.Interface || returnTypeDef is ViaductSchema.Union
        val selectionBuilderType = if (needsSelection) "${returnTypeDef.name}DslBuilder" else null
        val hasArgs = fieldDef.args.isNotEmpty()
        // Pre-computed strings for the template
        val parameterSignature: String = parameters.joinToString(", ") { "${it.escapedName}: ${it.kotlinType}" }
        val parameterSerializers: String = parameters.joinToString(", ") { "\"${it.argName}: \" + serializeValue(${it.escapedName})" }
    }

    class ParameterModel(pkg: String, arg: ViaductSchema.HasDefaultValue, baseTypeMapper: BaseTypeMapper) {
        val escapedName = getEscapedFieldName(arg.name)
        val argName = arg.name
        val kotlinType = arg.kmType(JavaName(pkg).asKmName, baseTypeMapper, isInput = true).kotlinTypeString
    }
}

private val subscriptionDslSTGroup = stTemplate(
    """
@file:Suppress("warnings")

package <mdl.pkg>

fun subscription(name: String? = null, block: SubscriptionDslBuilder.() -> Unit): String {
    val builder = SubscriptionDslBuilder()
    builder.block()
    val operationName = name?.let { " ${'$'}it" } ?: ""
    return "subscription${'$'}operationName { ${'$'}{builder.build()} }"
}

class SubscriptionDslBuilder internal constructor() {
    private val fields = mutableListOf\<String>()

    private fun addField(name: String) {
        fields.add(name)
    }

<mdl.scalarFields: { f |
    fun <f.escapedName>(<f.parameterSignature>) {
<if(f.hasArgs)>
        val args = listOf(<f.parameterSerializers>).joinToString(", ")
        addField("<f.fieldName>(${'$'}args)")
<else>
        addField("<f.fieldName>")
<endif>
    \}
}; separator="\n">

<mdl.complexFields: { f |
    fun <f.escapedName>(<f.parameterSignature><if(f.hasArgs)>, <endif>block: <f.selectionBuilderType>.() -> Unit) {
<if(f.hasArgs)>
        val args = listOf(<f.parameterSerializers>).joinToString(", ")
        val fieldStr = "<f.fieldName>(${'$'}args)"
<else>
        val fieldStr = "<f.fieldName>"
<endif>
        val nestedBuilder = <f.selectionBuilderType>()
        nestedBuilder.block()
        addField("${'$'}fieldStr { ${'$'}{nestedBuilder.build()\} \}")
    \}
}; separator="\n">

    internal fun build(): String = fields.joinToString(" ")

    private fun serializeValue(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> "\"${'$'}{value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
            is Boolean -> value.toString()
            is Number -> value.toString()
            is Enum\<*> -> value.name
            is List\<*> -> "[" + value.joinToString(", ") { serializeValue(it) } + "]"
            else -> {
                val inputData = value::class.java.getMethod("getInputData").invoke(value) as? Map\<*, *>
                inputData?.let { serializeInputObject(it) } ?: value.toString()
            }
        }
    }

    private fun serializeInputObject(map: Map\<*, *>): String {
        return map.entries.joinToString(", ", "{", "}") { (k, v) ->
            "${'$'}k: ${'$'}{serializeValue(v)}"
        }
    }
}
"""
)

private class SubscriptionDslModelImpl(
    override val pkg: String,
    subscriptionDef: ViaductSchema.Object,
    baseTypeMapper: BaseTypeMapper
) : SubscriptionDslModel {
    override val scalarFields: List<SubscriptionDslModel.SubscriptionFieldModel>
    override val complexFields: List<SubscriptionDslModel.SubscriptionFieldModel>

    init {
        val scalars = mutableListOf<SubscriptionDslModel.SubscriptionFieldModel>()
        val complex = mutableListOf<SubscriptionDslModel.SubscriptionFieldModel>()

        for (field in subscriptionDef.fields) {
            val returnType = field.type.baseTypeDef
            val isScalar = returnType is ViaductSchema.Scalar || returnType is ViaductSchema.Enum
            val fieldModel = SubscriptionDslModel.SubscriptionFieldModel(pkg, field, baseTypeMapper)
            if (isScalar) scalars.add(fieldModel) else complex.add(fieldModel)
        }

        scalarFields = scalars
        complexFields = complex
    }
}
