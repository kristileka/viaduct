package viaduct.tenant.codegen.dsl

import kotlin.test.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import viaduct.codegen.st.STContents
import viaduct.graphql.schema.ViaductSchema
import viaduct.tenant.codegen.bytecode.config.ViaductBaseTypeMapper
import viaduct.tenant.codegen.kotlingen.bytecode.mkSchema

class ObjectDslGenTest {

    private fun generateObjectDsl(sdl: String, typeName: String): STContents {
        val schema = mkSchema(sdl)
        val baseTypeMapper = ViaductBaseTypeMapper(schema)
        val objectType = schema.types[typeName]!! as ViaductSchema.Object
        return objectDslGen(TestPackages.DSL_PACKAGE, objectType, baseTypeMapper)
    }

    @Nested
    @DisplayName("Builder Class Generation")
    inner class BuilderClassTests {

        @Test
        fun `generates named builder class`() {
            val result = generateObjectDsl(
                """
                type Query { user: User }
                type User {
                    id: ID
                    name: String
                }
                """.trimIndent(),
                "User"
            ).toString()

            assertTrue(result.contains("class UserDslBuilder internal constructor()"))
        }

        @Test
        fun `generates private fields list and addField method`() {
            val result = generateObjectDsl(
                """
                type Query { user: User }
                type User {
                    id: ID
                }
                """.trimIndent(),
                "User"
            ).toString()

            assertTrue(result.contains("private val fields = mutableListOf<String>()"))
            assertTrue(result.contains("private fun addField(name: String)"))
        }
    }

    @Nested
    @DisplayName("Scalar Field Generation")
    inner class ScalarFieldTests {

        @Test
        fun `generates scalar fields as properties`() {
            val result = generateObjectDsl(
                """
                type Query { user: User }
                type User {
                    id: ID
                    name: String
                }
                """.trimIndent(),
                "User"
            ).toString()

            assertTrue(result.contains("val id: Unit"))
            assertTrue(result.contains("val name: Unit"))
        }

        @Test
        fun `generates addField calls in property getters`() {
            val result = generateObjectDsl(
                """
                type Query { user: User }
                type User {
                    id: ID
                    name: String
                }
                """.trimIndent(),
                "User"
            ).toString()

            assertTrue(result.contains("addField(\"id\")"))
            assertTrue(result.contains("addField(\"name\")"))
        }
    }

    @Nested
    @DisplayName("Complex Field Generation")
    inner class ComplexFieldTests {

        @Test
        fun `generates complex fields as functions with builder block`() {
            val result = generateObjectDsl(
                """
                type Query { user: User }
                type User {
                    id: ID
                    address: Address
                }
                type Address {
                    street: String
                }
                """.trimIndent(),
                "User"
            ).toString()

            assertTrue(result.contains("fun address(block: AddressDslBuilder.() -> Unit)"))
        }

        @Test
        fun `generates fields with arguments as functions`() {
            val result = generateObjectDsl(
                """
                type Query { user: User }
                type User {
                    id: ID
                    posts(limit: Int): [Post]
                }
                type Post {
                    id: ID
                }
                """.trimIndent(),
                "User"
            ).toString()

            assertTrue(result.contains("fun posts("))
            // Int? is nullable in GraphQL, check for limit parameter
            assertTrue(result.contains("limit:"))
        }

        @Test
        fun `generates argument serialization for fields with args`() {
            val result = generateObjectDsl(
                """
                type Query { user: User }
                type User {
                    posts(limit: Int, offset: Int): [Post]
                }
                type Post {
                    id: ID
                }
                """.trimIndent(),
                "User"
            ).toString()

            assertTrue(result.contains("serializeValue(limit)"))
            assertTrue(result.contains("serializeValue(offset)"))
        }
    }
}
