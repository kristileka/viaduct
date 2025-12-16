package viaduct.tenant.codegen.dsl

import getEscapedFieldName
import viaduct.codegen.st.STContents
import viaduct.codegen.st.stTemplate
import viaduct.graphql.schema.ViaductSchema

fun nodeInterfaceDslGen(
    pkg: String,
    nodeInterface: ViaductSchema.Interface,
    implementingTypes: List<ViaductSchema.Object>
) = STContents(
    nodeInterfaceSTGroup,
    NodeInterfaceModelImpl(pkg, nodeInterface, implementingTypes)
)

private interface NodeInterfaceModel {
    val pkg: String
    val interfaceName: String
    val implementingTypes: List<ImplementingTypeModel>
    val commonFields: List<CommonFieldModel>

    class ImplementingTypeModel(typeDef: ViaductSchema.Object) {
        val typeName: String = typeDef.name
        val builderClassName: String = "${typeDef.name}DslBuilder"
        val fragmentMethodName: String = "on${typeDef.name}"
    }

    class CommonFieldModel(fieldDef: ViaductSchema.Field) {
        val fieldName: String = fieldDef.name
        val escapedName: String = getEscapedFieldName(fieldDef.name)
    }
}

private val nodeInterfaceSTGroup = stTemplate(
    """
@file:Suppress("warnings")

package <mdl.pkg>

class <mdl.interfaceName>DslBuilder internal constructor() {
    private val fields = mutableListOf\<String>()

    private fun addField(name: String) {
        fields.add(name)
    }

<mdl.commonFields: { f |    val <f.escapedName>: Unit
        get() {
            addField("<f.fieldName>")
        \}
}; separator="\n">

<mdl.implementingTypes: { t |    fun <t.fragmentMethodName>(block: <t.builderClassName>.() -> Unit) {
        val nestedBuilder = <t.builderClassName>()
        nestedBuilder.block()
        addField("... on <t.typeName> { ${'$'}{nestedBuilder.build()\} \}")
    \}
}; separator="\n">

    internal fun build(): String = fields.joinToString(" ")
}
"""
)

private class NodeInterfaceModelImpl(
    override val pkg: String,
    nodeInterface: ViaductSchema.Interface,
    implementingTypes: List<ViaductSchema.Object>
) : NodeInterfaceModel {
    override val interfaceName: String = nodeInterface.name

    override val implementingTypes: List<NodeInterfaceModel.ImplementingTypeModel> =
        implementingTypes.map { NodeInterfaceModel.ImplementingTypeModel(it) }

    override val commonFields: List<NodeInterfaceModel.CommonFieldModel> =
        nodeInterface.fields
            .filter { field ->
                val returnType = field.type.baseTypeDef
                val isScalar = returnType is ViaductSchema.Scalar || returnType is ViaductSchema.Enum
                isScalar && field.args.isEmpty()
            }
            .map { NodeInterfaceModel.CommonFieldModel(it) }
}
