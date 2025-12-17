package viaduct.tenant.codegen.dsl

import getEscapedFieldName
import viaduct.codegen.km.kotlinTypeString
import viaduct.codegen.st.STContents
import viaduct.codegen.st.stTemplate
import viaduct.codegen.utils.JavaName
import viaduct.graphql.schema.ViaductSchema
import viaduct.tenant.codegen.bytecode.config.BaseTypeMapper
import viaduct.tenant.codegen.bytecode.config.kmType

fun objectDslGen(
    pkg: String,
    modelPackage: String,
    objectDef: ViaductSchema.Object,
    baseTypeMapper: BaseTypeMapper
) = STContents(
    objectDslSTGroup,
    ObjectDslModelImpl(pkg, modelPackage, objectDef, baseTypeMapper)
)

private interface ObjectDslModel {
    val pkg: String
    val className: String
    val scalarFields: List<ScalarFieldModel>
    val complexFields: List<ComplexFieldModel>

    class ScalarFieldModel(fieldDef: ViaductSchema.Field) {
        val escapedName: String = getEscapedFieldName(fieldDef.name)
        val fieldName: String = fieldDef.name
    }

    class ComplexFieldModel(
        modelPackage: String,
        fieldDef: ViaductSchema.Field,
        baseTypeMapper: BaseTypeMapper
    ) {
        val escapedName: String = getEscapedFieldName(fieldDef.name)
        val fieldName: String = fieldDef.name
        val parameters: List<ParameterModel> = fieldDef.args.map {
            ParameterModel(modelPackage, it, baseTypeMapper)
        }
        val returnTypeDef: ViaductSchema.TypeDef = fieldDef.type.baseTypeDef
        val needsSelection: Boolean = returnTypeDef is ViaductSchema.Object ||
            returnTypeDef is ViaductSchema.Interface ||
            returnTypeDef is ViaductSchema.Union
        val selectionBuilderType: String? = if (needsSelection) "${returnTypeDef.name}DslBuilder" else null
        val hasArgs: Boolean = fieldDef.args.isNotEmpty()
        // Pre-computed strings for the template
        val parameterSignature: String = parameters.joinToString(", ") { "${it.escapedName}: ${it.kotlinType}" }
        val parameterSerializers: String = parameters.joinToString(", ") { "\"${it.argName}: \" + serializeValue(${it.escapedName})" }
    }

    class ParameterModel(
        modelPackage: String,
        arg: ViaductSchema.HasDefaultValue,
        baseTypeMapper: BaseTypeMapper
    ) {
        val escapedName: String = getEscapedFieldName(arg.name)
        val argName: String = arg.name
        private val grtsPackage = modelPackage.replace(".dsl.model", ".grts")
        val kotlinType: String = arg.kmType(JavaName(modelPackage).asKmName, baseTypeMapper, isInput = true).kotlinTypeString.replaceGlobalIdWithString()
    }
}

private fun String.replaceGlobalIdWithString(): String {
    val globalIdPattern = Regex("""viaduct\.api\.globalid\.GlobalID<[^>]+>""")
    return this.replace(globalIdPattern, "String")
}

private val objectDslSTGroup = stTemplate(
    """
@file:Suppress("warnings")

package <mdl.pkg>

class <mdl.className> internal constructor() {
    private val fields = mutableListOf\<String>()

    private fun addField(name: String) {
        fields.add(name)
    }

<mdl.scalarFields: { f |
    val <f.escapedName>: Unit
        get() {
            addField("<f.fieldName>")
        \}
}; separator="\n">

<mdl.complexFields: { f |
    fun <f.escapedName>(<f.parameterSignature><if(f.needsSelection)><if(f.hasArgs)>, <endif>block: <f.selectionBuilderType>.() -> Unit<endif>) {
<if(f.hasArgs)>
        val args = listOf(<f.parameterSerializers>).joinToString(", ")
        val fieldStr = "<f.fieldName>(${'$'}args)"
<else>
        val fieldStr = "<f.fieldName>"
<endif>
<if(f.needsSelection)>
        val nestedBuilder = <f.selectionBuilderType>()
        nestedBuilder.block()
        addField("${'$'}fieldStr { ${'$'}{nestedBuilder.build()\} \}")
<else>
        addField(fieldStr)
<endif>
    \}
}; separator="\n">

    internal fun build(): String = fields.joinToString(" ")

    private fun serializeValue(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> "\\"" + value.replace("\\\\", "\\\\\\\\").replace("\\"", "\\\\\\"") + "\\""
            is Boolean -> value.toString()
            is Number -> value.toString()
            is Enum\<*> -> value.name
            is List\<*> -> "[" + value.joinToString(", ") { serializeValue(it) } + "]"
            else -> {
                // Try to get toInputData method (for DSL input models)
                val toInputDataMethod = value::class.java.methods.find { it.name == "toInputData" }
                if (toInputDataMethod != null) {
                    val inputData = toInputDataMethod.invoke(value) as? Map\<*, *>
                    inputData?.let { serializeInputObject(it) } ?: value.toString()
                } else {
                    // Fallback to getInputData for legacy GRTS support
                    val getInputDataMethod = value::class.java.methods.find { it.name == "getInputData" }
                    val inputData = getInputDataMethod?.invoke(value) as? Map\<*, *>
                    inputData?.let { serializeInputObject(it) } ?: value.toString()
                }
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

private class ObjectDslModelImpl(
    override val pkg: String,
    private val modelPackage: String,
    objectDef: ViaductSchema.Object,
    baseTypeMapper: BaseTypeMapper
) : ObjectDslModel {
    override val className: String = "${objectDef.name}DslBuilder"
    override val scalarFields: List<ObjectDslModel.ScalarFieldModel>
    override val complexFields: List<ObjectDslModel.ComplexFieldModel>

    init {
        val scalars = mutableListOf<ObjectDslModel.ScalarFieldModel>()
        val complex = mutableListOf<ObjectDslModel.ComplexFieldModel>()

        for (field in objectDef.fields) {
            val returnType = field.type.baseTypeDef
            val isScalar = returnType is ViaductSchema.Scalar || returnType is ViaductSchema.Enum
            val hasArgs = field.args.isNotEmpty()

            if (isScalar && !hasArgs) {
                scalars.add(ObjectDslModel.ScalarFieldModel(field))
            } else {
                complex.add(ObjectDslModel.ComplexFieldModel(modelPackage, field, baseTypeMapper))
            }
        }

        scalarFields = scalars
        complexFields = complex
    }
}
