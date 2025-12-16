package viaduct.tenant.codegen.dsl

import viaduct.codegen.km.kotlinTypeString
import viaduct.codegen.st.STContents
import viaduct.codegen.st.stTemplate
import viaduct.codegen.utils.JavaName
import viaduct.graphql.schema.ViaductSchema
import viaduct.tenant.codegen.bytecode.config.BaseTypeMapper
import viaduct.tenant.codegen.bytecode.config.kmType

fun inputDslGen(
    pkg: String,
    inputDef: ViaductSchema.Input,
    baseTypeMapper: BaseTypeMapper
) = STContents(
    inputDslSTGroup,
    InputDslModelImpl(pkg, inputDef, baseTypeMapper)
)

private interface InputDslModel {
    val pkg: String
    val className: String
    val fields: List<InputFieldModel>

    class InputFieldModel(
        pkg: String,
        field: ViaductSchema.HasDefaultValue,
        baseTypeMapper: BaseTypeMapper
    ) {
        val fieldName: String = field.name
        private val baseKotlinType: String = field.kmType(
            JavaName(pkg).asKmName,
            baseTypeMapper,
            isInput = true
        ).kotlinTypeString.simplifyKotlinType().replaceGlobalIdPackage(pkg)

        // Check if field has a default value without throwing exception
        private val defaultValueResult: Pair<Boolean, Any?> = try {
            val value = field.defaultValue
            true to value
        } catch (e: NoSuchElementException) {
            false to null
        }

        val hasDefault: Boolean = defaultValueResult.first

        // Make the type nullable if there's no default value and it's not already nullable
        val kotlinType: String = if (!hasDefault && !baseKotlinType.endsWith("?")) {
            "$baseKotlinType?"
        } else {
            baseKotlinType
        }

        val defaultValue: String? = if (hasDefault) {
            defaultValueResult.second?.let { formatDefaultValue(it) } ?: "null"
        } else {
            "null"
        }

        private fun formatDefaultValue(value: Any): String {
            return when (value) {
                is String -> "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
                is Boolean, is Number -> value.toString()
                is List<*> -> "[" + value.joinToString(", ") { formatDefaultValue(it ?: "null") } + "]"
                else -> "null"
            }
        }
    }
}

private fun String.simplifyKotlinType(): String = this.removePrefix("kotlin.")

private fun String.replaceGlobalIdPackage(modelPackage: String): String {
    // Replace GlobalID type parameters from model package to grts package
    // Example: viaduct.api.globalid.GlobalID<viaduct.api.dsl.model.Film>
    // becomes: viaduct.api.globalid.GlobalID<viaduct.api.grts.Film>
    val grtsPackage = modelPackage.replace(".dsl.model", ".grts")
    return this.replace("<$modelPackage.", "<$grtsPackage.")
}

private val inputDslSTGroup = stTemplate(
    """
@file:Suppress("warnings")

package <mdl.pkg>

data class <mdl.className>(
<mdl.fields: { f |    val <f.fieldName>: <f.kotlinType> = <f.defaultValue>}; separator=",\n">
) {
    fun toInputData(): Map\<String, Any?> {
        return buildMap {
<mdl.fields: { f |            <f.fieldName>?.let { put("<f.fieldName>", it) \}}; separator="\n">
        }
    }
}
"""
)

private class InputDslModelImpl(
    override val pkg: String,
    inputDef: ViaductSchema.Input,
    baseTypeMapper: BaseTypeMapper
) : InputDslModel {
    override val className: String = inputDef.name
    override val fields: List<InputDslModel.InputFieldModel> =
        inputDef.fields.map { InputDslModel.InputFieldModel(pkg, it, baseTypeMapper) }
}
