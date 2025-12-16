package viaduct.tenant.codegen.dsl

import java.io.File
import viaduct.graphql.schema.ViaductSchema
import viaduct.tenant.codegen.bytecode.config.BaseTypeMapper

class DslFilesBuilder(
    private val pkg: String,
    private val outputDir: File,
    private val baseTypeMapper: BaseTypeMapper
) {
    private val packageDir: File = File(outputDir, pkg.replace('.', '/'))

    fun generate(schema: ViaductSchema) {
        packageDir.mkdirs()
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

        for (typeName in objectTypesNeedingBuilders) {
            val typeDef = schema.types[typeName]
            if (typeDef is ViaductSchema.Object && !isRootType(typeName)) {
                generateObjectDsl(typeDef)
            }
        }

        generateNodeInterfaceSupport(schema, objectTypesNeedingBuilders)
    }

    private fun generateQueryDsl(queryType: ViaductSchema.Object) {
        val dst = File(packageDir, "QueryDsl.kt")
        queryDslGen(pkg, queryType, baseTypeMapper).write(dst)
    }

    private fun generateMutationDsl(mutationType: ViaductSchema.Object) {
        val dst = File(packageDir, "MutationDsl.kt")
        mutationDslGen(pkg, mutationType, baseTypeMapper).write(dst)
    }

    private fun generateObjectDsl(objectType: ViaductSchema.Object) {
        val dst = File(packageDir, "${objectType.name}DslBuilder.kt")
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
                    val dst = File(packageDir, "${typeName}DslBuilder.kt")
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
