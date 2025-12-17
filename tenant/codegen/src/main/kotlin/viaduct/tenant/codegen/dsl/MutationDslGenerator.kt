/**
 * Mutation DSL Generator.
 *
 * Generates type-safe Kotlin DSL code for building GraphQL mutations.
 *
 * ## Key Difference from Query DSL
 *
 * Unlike queries where scalar fields without arguments are properties,
 * **all mutation fields are generated as functions**. This design choice
 * reflects that mutations are operations with side effects.
 *
 * Input types are passed as `Map<String, Any?>` for idiomatic Kotlin DSL usage.
 *
 * @see QueryDslGenerator for query generation
 * @see ObjectDslGenerator for nested object builder generation
 */
package viaduct.tenant.codegen.dsl

import getEscapedFieldName
import viaduct.codegen.st.STContents
import viaduct.codegen.st.stTemplate
import viaduct.graphql.schema.ViaductSchema
import viaduct.tenant.codegen.bytecode.config.BaseTypeMapper

// =============================================================================
// Public API
// =============================================================================

/**
 * Generates the Mutation DSL code for a GraphQL Mutation type.
 *
 * @param pkg The target package name for the generated DSL classes
 * @param mutationDef The GraphQL Mutation object type definition
 * @param baseTypeMapper The type mapper for GraphQL to Kotlin type conversion
 * @return STContents ready to be written to a file
 */
fun mutationDslGen(
    pkg: String,
    mutationDef: ViaductSchema.Object,
    baseTypeMapper: BaseTypeMapper
): STContents = STContents(
    MUTATION_DSL_TEMPLATE,
    MutationDslModelImpl(pkg, mutationDef, baseTypeMapper)
)

// =============================================================================
// Template Model Interface
// =============================================================================

private interface MutationDslModel {
    val pkg: String
    val scalarFields: List<ScalarMutationFieldModel>
    val complexFields: List<ComplexMutationFieldModel>
}

// =============================================================================
// Field Model Classes
// =============================================================================

/**
 * Model for scalar mutation fields. All are generated as functions.
 */
private class ScalarMutationFieldModel(
    fieldDef: ViaductSchema.Field,
    pkg: String,
    baseTypeMapper: BaseTypeMapper
) {
    val escapedName: String = getEscapedFieldName(fieldDef.name)
    val fieldName: String = fieldDef.name

    val parameters: List<FieldParameterModel> = fieldDef.args.map {
        FieldParameterModel(it, pkg, baseTypeMapper)
    }

    val hasArgs: Boolean = fieldDef.args.isNotEmpty()
    val parameterSignature: String = buildParameterSignature(parameters, includeAlias = true)
    val parameterSerializers: String = buildParameterSerializers(parameters)
}

/**
 * Model for complex mutation fields that return objects/interfaces/unions.
 */
private class ComplexMutationFieldModel(
    fieldDef: ViaductSchema.Field,
    pkg: String,
    baseTypeMapper: BaseTypeMapper
) {
    val escapedName: String = getEscapedFieldName(fieldDef.name)
    val fieldName: String = fieldDef.name

    val parameters: List<FieldParameterModel> = fieldDef.args.map {
        FieldParameterModel(it, pkg, baseTypeMapper)
    }

    private val returnTypeDef: ViaductSchema.TypeDef = fieldDef.type.baseTypeDef

    val selectionBuilderType: String = "${returnTypeDef.name}DslBuilder"
    val hasArgs: Boolean = fieldDef.args.isNotEmpty()
    val parameterSignature: String = buildParameterSignature(parameters, includeAlias = true)
    val parameterSerializers: String = buildParameterSerializers(parameters)
}

// =============================================================================
// Template Model Implementation
// =============================================================================

private class MutationDslModelImpl(
    override val pkg: String,
    mutationDef: ViaductSchema.Object,
    baseTypeMapper: BaseTypeMapper
) : MutationDslModel {

    override val scalarFields: List<ScalarMutationFieldModel>
    override val complexFields: List<ComplexMutationFieldModel>

    init {
        val scalars = mutableListOf<ScalarMutationFieldModel>()
        val complex = mutableListOf<ComplexMutationFieldModel>()

        for (field in mutationDef.fields) {
            val returnType = field.type.baseTypeDef

            if (returnType.isScalarOrEnum()) {
                scalars.add(ScalarMutationFieldModel(field, pkg, baseTypeMapper))
            } else {
                complex.add(ComplexMutationFieldModel(field, pkg, baseTypeMapper))
            }
        }

        scalarFields = scalars
        complexFields = complex
    }
}

// =============================================================================
// StringTemplate Definition
// =============================================================================

private val MUTATION_DSL_TEMPLATE = stTemplate(
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
        val aliasPrefix = if (alias != null) alias + ": " else ""
<if(f.hasArgs)>
        val args = listOf(<f.parameterSerializers>).joinToString(", ")
        addField(aliasPrefix + "<f.fieldName>(${'$'}args)")
<else>
        addField(aliasPrefix + "<f.fieldName>")
<endif>
    \}
}; separator="\n">

<mdl.complexFields: { f |
    fun <f.escapedName>(<f.parameterSignature><if(f.hasArgs)>, <endif>block: <f.selectionBuilderType>.() -> Unit) {
        val aliasPrefix = if (alias != null) alias + ": " else ""
<if(f.hasArgs)>
        val args = listOf(<f.parameterSerializers>).joinToString(", ")
        val fieldStr = aliasPrefix + "<f.fieldName>(${'$'}args)"
<else>
        val fieldStr = aliasPrefix + "<f.fieldName>"
<endif>
        val nestedBuilder = <f.selectionBuilderType>()
        nestedBuilder.block()
        addField(fieldStr + " { " + nestedBuilder.build() + " \}")
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
            is Map\<*, *> -> value.entries.joinToString(", ", "{", "}") { (k, v) ->
                "${'$'}k: ${'$'}{serializeValue(v)}"
            }
            is List\<*> -> "[" + value.joinToString(", ") { serializeValue(it) } + "]"
            else -> value.toString()
        }
    }
}
"""
)
