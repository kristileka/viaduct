/**
 * DSL Files Builder - Main Orchestrator for GraphQL DSL Code Generation.
 *
 * This class coordinates the generation of all DSL files from a GraphQL schema.
 * It serves as the entry point for the DSL code generation pipeline.
 *
 * ## Overview
 *
 * The DSL generation system creates type-safe Kotlin builders for GraphQL operations.
 * Given a GraphQL schema, it produces:
 *
 * 1. **QueryDsl.kt** - Builder for GraphQL queries
 * 2. **MutationDsl.kt** - Builder for GraphQL mutations (if schema has mutations)
 * 3. **{ObjectType}DslBuilder.kt** - Builders for each object type used in selections
 * 4. **{Interface}DslBuilder.kt** - Builders for interface types with fragment support
 *
 * Input types are passed as `Map<String, Any?>` for a more idiomatic Kotlin DSL experience.
 *
 * ## Output Directory Structure
 *
 * ```
 * outputDir/
 * └── {pkg}/
 *     ├── QueryDsl.kt
 *     ├── MutationDsl.kt
 *     ├── UserDslBuilder.kt
 *     ├── PostDslBuilder.kt
 *     └── NodeDslBuilder.kt
 * ```
 *
 * ## Usage
 *
 * ```kotlin
 * val builder = DslFilesBuilder(
 *     pkg = "com.example.api.dsl",
 *     outputDir = File("generated"),
 *     baseTypeMapper = ViaductBaseTypeMapper(schema)
 * )
 * builder.generate(schema)
 * ```
 *
 * @property pkg The base package name for generated DSL classes
 * @property outputDir The root output directory for generated files
 * @property baseTypeMapper Type mapper for GraphQL to Kotlin type conversion
 *
 * @see QueryDslGenerator for query DSL generation details
 * @see MutationDslGenerator for mutation DSL generation details
 * @see ObjectDslGenerator for object builder generation details
 * @see NodeInterfaceDslGenerator for interface builder generation details
 */
package viaduct.tenant.codegen.dsl

import java.io.File
import viaduct.graphql.schema.ViaductSchema
import viaduct.tenant.codegen.bytecode.config.BaseTypeMapper

/**
 * Orchestrator for generating GraphQL DSL files from a schema.
 *
 * @param pkg The target package name for generated DSL classes
 * @param outputDir The root output directory for generated files
 * @param baseTypeMapper The type mapper for GraphQL to Kotlin type conversion
 */
class DslFilesBuilder(
    private val pkg: String,
    private val outputDir: File,
    private val baseTypeMapper: BaseTypeMapper
) {
    /** Directory for DSL builder files */
    private val packageDir: File = File(outputDir, pkg.replace('.', '/'))

    /**
     * Generates all DSL files from the provided GraphQL schema.
     *
     * This method orchestrates the complete code generation process:
     * 1. Creates output directory
     * 2. Generates Query DSL (if schema has Query type)
     * 3. Generates Mutation DSL (if schema has Mutation type)
     * 4. Generates Object DSL builders for all referenced types
     * 5. Generates Interface DSL builders with fragment support
     *
     * @param schema The GraphQL schema to generate DSL for
     */
    fun generate(schema: ViaductSchema) {
        packageDir.mkdirs()

        val objectTypesNeedingBuilders = mutableSetOf<String>()

        // Generate Query DSL and collect needed object builders
        schema.types[QUERY_TYPE]?.let { queryType ->
            if (queryType is ViaductSchema.Object) {
                generateQueryDsl(queryType)
                collectNeededBuilders(queryType, objectTypesNeedingBuilders, schema)
            }
        }

        // Generate Mutation DSL and collect needed object builders
        schema.types[MUTATION_TYPE]?.let { mutationType ->
            if (mutationType is ViaductSchema.Object) {
                generateMutationDsl(mutationType)
                collectNeededBuilders(mutationType, objectTypesNeedingBuilders, schema)
            }
        }

        // Generate Object DSL builders for all collected types
        generateObjectBuilders(objectTypesNeedingBuilders, schema)

        // Generate Interface DSL builders with fragment methods
        generateInterfaceBuilders(schema, objectTypesNeedingBuilders)
    }

    // =========================================================================
    // Individual Generator Methods
    // =========================================================================

    /**
     * Generates QueryDsl.kt for the GraphQL Query type.
     */
    private fun generateQueryDsl(queryType: ViaductSchema.Object) {
        val destination = File(packageDir, "QueryDsl.kt")
        queryDslGen(pkg, queryType, baseTypeMapper).write(destination)
    }

    /**
     * Generates MutationDsl.kt for the GraphQL Mutation type.
     */
    private fun generateMutationDsl(mutationType: ViaductSchema.Object) {
        val destination = File(packageDir, "MutationDsl.kt")
        mutationDslGen(pkg, mutationType, baseTypeMapper).write(destination)
    }

    /**
     * Generates a DSL builder for a specific GraphQL Object type.
     */
    private fun generateObjectDsl(objectType: ViaductSchema.Object) {
        val destination = File(packageDir, "${objectType.name}DslBuilder.kt")
        objectDslGen(pkg, objectType, baseTypeMapper).write(destination)
    }

    // =========================================================================
    // Batch Generation Methods
    // =========================================================================

    /**
     * Generates Object DSL builders for all types in the collection.
     *
     * Excludes root types (Query, Mutation, Subscription) as they have
     * their own dedicated generators.
     */
    private fun generateObjectBuilders(
        typeNames: Set<String>,
        schema: ViaductSchema
    ) {
        for (typeName in typeNames) {
            val typeDef = schema.types[typeName]
            if (typeDef is ViaductSchema.Object && !isRootType(typeName)) {
                generateObjectDsl(typeDef)
            }
        }
    }

    /**
     * Generates Interface DSL builders for all interfaces in the schema.
     *
     * Also adds implementing types to the needed builders set since
     * fragment methods reference their builders.
     */
    private fun generateInterfaceBuilders(
        schema: ViaductSchema,
        objectTypesNeedingBuilders: MutableSet<String>
    ) {
        for ((typeName, typeDef) in schema.types) {
            if (typeDef is ViaductSchema.Interface) {
                val implementingTypes = findImplementingTypes(schema, typeName)

                if (implementingTypes.isNotEmpty()) {
                    val destination = File(packageDir, "${typeName}DslBuilder.kt")
                    nodeInterfaceDslGen(pkg, typeDef, implementingTypes).write(destination)

                    // Ensure builders exist for all implementing types
                    implementingTypes.forEach { objectTypesNeedingBuilders.add(it.name) }
                }
            }
        }
    }

    // =========================================================================
    // Type Discovery Methods
    // =========================================================================

    /**
     * Recursively collects all object types that need DSL builders.
     *
     * Starting from a root type (Query or Mutation), traverses all fields
     * to find Object, Interface, and Union types that will need builders
     * for nested field selection.
     *
     * @param typeDef The type to scan for referenced types
     * @param collectors The set to add discovered type names to
     * @param schema The schema for looking up type definitions
     */
    private fun collectNeededBuilders(
        typeDef: ViaductSchema.Object,
        collectors: MutableSet<String>,
        schema: ViaductSchema
    ) {
        for (field in typeDef.fields) {
            when (val returnType = field.type.baseTypeDef) {
                is ViaductSchema.Object -> {
                    // Recursively collect from Object types, avoiding cycles
                    if (!isRootType(returnType.name) && collectors.add(returnType.name)) {
                        collectNeededBuilders(returnType, collectors, schema)
                    }
                }
                is ViaductSchema.Interface, is ViaductSchema.Union -> {
                    // Interface and Union types need builders but don't recurse
                    collectors.add(returnType.name)
                }
            }
        }
    }

    /**
     * Finds all Object types that implement a given interface.
     *
     * @param schema The schema to search
     * @param interfaceName The interface name to find implementations for
     * @return List of Object types implementing the interface
     */
    private fun findImplementingTypes(
        schema: ViaductSchema,
        interfaceName: String
    ): List<ViaductSchema.Object> =
        schema.types.values
            .filterIsInstance<ViaductSchema.Object>()
            .filter { obj -> obj.supers.any { it.name == interfaceName } }

    /**
     * Checks if a type name is a GraphQL root operation type.
     *
     * Root types (Query, Mutation, Subscription) have dedicated generators
     * and should not get generic Object DSL builders.
     */
    private fun isRootType(name: String): Boolean = name in ROOT_TYPES

    companion object {
        /** GraphQL Query root type name */
        private const val QUERY_TYPE = "Query"

        /** GraphQL Mutation root type name */
        private const val MUTATION_TYPE = "Mutation"

        /** Set of GraphQL root operation type names */
        private val ROOT_TYPES = setOf("Query", "Mutation", "Subscription")
    }
}
