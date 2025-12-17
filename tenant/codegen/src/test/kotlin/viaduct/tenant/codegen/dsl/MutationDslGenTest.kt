package viaduct.tenant.codegen.dsl

import kotlin.test.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import viaduct.codegen.st.STContents
import viaduct.graphql.schema.ViaductSchema
import viaduct.tenant.codegen.bytecode.config.ViaductBaseTypeMapper
import viaduct.tenant.codegen.kotlingen.bytecode.mkSchema

class MutationDslGenTest {

    private fun generateMutationDsl(sdl: String): STContents {
        val schema = mkSchema(sdl)
        val baseTypeMapper = ViaductBaseTypeMapper(schema)
        val mutationType = schema.types["Mutation"]!! as ViaductSchema.Object
        return mutationDslGen(TestPackages.DSL_PACKAGE, mutationType, baseTypeMapper)
    }

    @Nested
    @DisplayName("Mutation Function Generation")
    inner class MutationFunctionTests {

        @Test
        fun `generates mutation function with correct signature`() {
            val result = generateMutationDsl(
                """
                type Query { empty: Int }
                type Mutation {
                    updateUser: Boolean
                }
                """.trimIndent()
            ).toString()

            assertTrue(result.contains("fun mutation(name: String? = null, block: MutationDslBuilder.() -> Unit): String"))
        }
    }

    @Nested
    @DisplayName("Builder Class Generation")
    inner class BuilderClassTests {

        @Test
        fun `generates MutationDslBuilder class with internal constructor`() {
            val result = generateMutationDsl(
                """
                type Query { empty: Int }
                type Mutation {
                    updateUser: Boolean
                }
                """.trimIndent()
            ).toString()

            assertTrue(result.contains("class MutationDslBuilder internal constructor()"))
        }
    }

    @Nested
    @DisplayName("Scalar Field Generation")
    inner class ScalarFieldTests {

        @Test
        fun `generates scalar mutation fields as functions not properties`() {
            val result = generateMutationDsl(
                """
                type Query { empty: Int }
                type Mutation {
                    deleteUser(id: ID!): Boolean
                }
                """.trimIndent()
            ).toString()

            assertTrue(result.contains("fun deleteUser("))
            // Should NOT be a property
            assertFalse(result.contains("val deleteUser: Unit"))
        }

        @Test
        fun `generates scalar fields without args as functions`() {
            val result = generateMutationDsl(
                """
                type Query { empty: Int }
                type Mutation {
                    ping: Boolean
                }
                """.trimIndent()
            ).toString()

            assertTrue(result.contains("fun ping("))
        }
    }

    @Nested
    @DisplayName("Complex Field Generation")
    inner class ComplexFieldTests {

        @Test
        fun `generates complex mutation fields with builder block`() {
            val result = generateMutationDsl(
                """
                type Query { empty: Int }
                type Mutation {
                    createUser(name: String!): User
                }
                type User {
                    id: ID
                }
                """.trimIndent()
            ).toString()

            assertTrue(result.contains("fun createUser("))
            assertTrue(result.contains("block: UserDslBuilder.() -> Unit"))
        }

        @Test
        fun `generates nested builder invocation`() {
            val result = generateMutationDsl(
                """
                type Query { empty: Int }
                type Mutation {
                    createUser(name: String!): User
                }
                type User {
                    id: ID
                }
                """.trimIndent()
            ).toString()

            assertTrue(result.contains("val nestedBuilder = UserDslBuilder()"))
            assertTrue(result.contains("nestedBuilder.block()"))
            assertTrue(result.contains("nestedBuilder.build()"))
        }
    }
}
