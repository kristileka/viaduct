package viaduct.tenant.codegen.dsl

import getEscapedFieldName
import viaduct.codegen.km.kotlinTypeString
import viaduct.codegen.st.STContents
import viaduct.codegen.st.stTemplate
import viaduct.codegen.utils.JavaName
import viaduct.graphql.schema.ViaductSchema
import viaduct.tenant.codegen.bytecode.config.BaseTypeMapper
import viaduct.tenant.codegen.bytecode.config.kmType

fun mutationDslGen(
    pkg: String,
    mutationDef: ViaductSchema.Object,
    baseTypeMapper: BaseTypeMapper
) = STContents(
    mutationDslSTGroup,
    MutationDslModelImpl(pkg, mutationDef, baseTypeMapper)
)

private interface MutationDslModel {
    val pkg: String
    val scalarFields: List<ScalarFieldModel>
    val complexFields: List<ComplexFieldModel>

    class ScalarFieldModel(
        pkg: String,
        fieldDef: ViaductSchema.Field,
        baseTypeMapper: BaseTypeMapper
    ) {
        val escapedName: String = getEscapedFieldName(fieldDef.name)
        val fieldName: String = fieldDef.name
        val parameters: List<ParameterModel> = fieldDef.args.map {
            ParameterModel(pkg, it, baseTypeMapper)
        }
        val hasArgs: Boolean = fieldDef.args.isNotEmpty()
        // Pre-computed strings for the template
        val parameterSignature: String = parameters.joinToString(", ") { "${it.escapedName}: ${it.kotlinType}" }
        val parameterSerializers: String = parameters.joinToString(", ") { "\"${it.argName}: \" + serializeValue(${it.escapedName})" }
    }

    class ComplexFieldModel(
        pkg: String,
        fieldDef: ViaductSchema.Field,
        baseTypeMapper: BaseTypeMapper
    ) {
        val escapedName: String = getEscapedFieldName(fieldDef.name)
        val fieldName: String = fieldDef.name
        val parameters: List<ParameterModel> = fieldDef.args.map {
            ParameterModel(pkg, it, baseTypeMapper)
        }
        val returnTypeDef: ViaductSchema.TypeDef = fieldDef.type.baseTypeDef
        val selectionBuilderType: String = "${returnTypeDef.name}DslBuilder"
        val hasArgs: Boolean = fieldDef.args.isNotEmpty()
        // Pre-computed strings for the template
        val parameterSignature: String = parameters.joinToString(", ") { "${it.escapedName}: ${it.kotlinType}" }
        val parameterSerializers: String = parameters.joinToString(", ") { "\"${it.argName}: \" + serializeValue(${it.escapedName})" }
    }

    class ParameterModel(
        pkg: String,
        arg: ViaductSchema.HasDefaultValue,
        baseTypeMapper: BaseTypeMapper
    ) {
        val escapedName: String = getEscapedFieldName(arg.name)
        val argName: String = arg.name
        val kotlinType: String = arg.kmType(JavaName(pkg).asKmName, baseTypeMapper, isInput = true).kotlinTypeString
    }
}

private val mutationDslSTGroup = stTemplate(
    """
@file:Suppress("warnings")

package <mdl.pkg>

fun mutation(name: String? = null, block: MutationDslBuilder.() -> Unit): String {
    val builder = MutationDslBuilder()
    builder.block()
    val operationName = name?.let { " ${'$'}it" } ?: ""
    return "mutation${'$'}operationName { ${'$'}{builder.build()} }"
}

class MutationDslBuilder internal constructor() {
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

private class MutationDslModelImpl(
    override val pkg: String,
    mutationDef: ViaductSchema.Object,
    baseTypeMapper: BaseTypeMapper
) : MutationDslModel {
    override val scalarFields: List<MutationDslModel.ScalarFieldModel>
    override val complexFields: List<MutationDslModel.ComplexFieldModel>

    init {
        val scalars = mutableListOf<MutationDslModel.ScalarFieldModel>()
        val complex = mutableListOf<MutationDslModel.ComplexFieldModel>()

        for (field in mutationDef.fields) {
            val returnType = field.type.baseTypeDef
            val isScalar = returnType is ViaductSchema.Scalar || returnType is ViaductSchema.Enum

            if (isScalar) {
                scalars.add(MutationDslModel.ScalarFieldModel(pkg, field, baseTypeMapper))
            } else {
                complex.add(MutationDslModel.ComplexFieldModel(pkg, field, baseTypeMapper))
            }
        }

        scalarFields = scalars
        complexFields = complex
    }
}
