package viaduct.tenant.codegen.dsl

import org.junit.jupiter.api.Nested
import kotlin.test.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import viaduct.codegen.st.STContents
import viaduct.graphql.schema.ViaductSchema
import viaduct.tenant.codegen.bytecode.config.ViaductBaseTypeMapper
import viaduct.tenant.codegen.kotlingen.bytecode.mkSchema

class QueryDslGenTest {

    private fun generateQueryDsl(sdl: String): STContents {
        val schema = mkSchema(sdl)
        val baseTypeMapper = ViaductBaseTypeMapper(schema)
        val queryType = schema.types["Query"]!! as ViaductSchema.Object
        return queryDslGen(TestPackages.DSL_PACKAGE, queryType, baseTypeMapper)
    }

    @Nested
    @DisplayName("Query Function Generation")
    inner class QueryFunctionTests {

        @Test
        fun `generates query function with correct signature`() {
            val result = generateQueryDsl(
                """
                type Query {
                    hello: String
                }
                """.trimIndent()
            ).toString()

            assertTrue(result.contains("fun query(name: String? = null, block: QueryDslBuilder.() -> Unit): String"))
        }

        @Test
        fun `generates query function that builds query string`() {
            val result = generateQueryDsl(
                """
                type Query {
                    hello: String
                }
                """.trimIndent()
            ).toString()

            assertTrue(result.contains("return \"query"))
            assertTrue(result.contains("builder.build()"))
        }
    }

    @Nested
    @DisplayName("Builder Class Generation")
    inner class BuilderClassTests {

        @Test
        fun `generates QueryDslBuilder class with internal constructor`() {
            val result = generateQueryDsl(
                """
                type Query {
                    hello: String
                }
                """.trimIndent()
            ).toString()

            assertTrue(result.contains("class QueryDslBuilder internal constructor()"))
        }

        @Test
        fun `generates private fields list`() {
            val result = generateQueryDsl(
                """
                type Query {
                    hello: String
                }
                """.trimIndent()
            ).toString()

            assertTrue(result.contains("private val fields = mutableListOf<String>()"))
        }

        @Test
        fun `generates build method`() {
            val result = generateQueryDsl(
                """
                type Query {
                    hello: String
                }
                """.trimIndent()
            ).toString()

            assertTrue(result.contains("internal fun build(): String = fields.joinToString(\" \")"))
        }
    }

    @Nested
    @DisplayName("Scalar Field Generation")
    inner class ScalarFieldTests {

        @Test
        fun `generates scalar fields as properties`() {
            val result = generateQueryDsl(
                """
                type Query {
                    greeting: String
                    count: Int
                }
                """.trimIndent()
            ).toString()

            assertTrue(result.contains("val greeting: Unit"))
            assertTrue(result.contains("val count: Unit"))
        }

        @Test
        fun `generates addField calls for scalar fields`() {
            val result = generateQueryDsl(
                """
                type Query {
                    greeting: String
                    count: Int
                }
                """.trimIndent()
            ).toString()

            assertTrue(result.contains("addField(\"greeting\")"))
            assertTrue(result.contains("addField(\"count\")"))
        }

        @Test
        fun `generates enum fields as properties`() {
            val result = generateQueryDsl(
                """
                type Query {
                    status: Status
                }
                enum Status {
                    ACTIVE
                    INACTIVE
                }
                """.trimIndent()
            ).toString()

            assertTrue(result.contains("val status: Unit"))
        }
    }

    @Nested
    @DisplayName("Complex Field Generation")
    inner class ComplexFieldTests {

        @Test
        fun `generates object fields as functions with builder block`() {
            val result = generateQueryDsl(
                """
                type Query {
                    user: User
                }
                type User {
                    id: ID
                }
                """.trimIndent()
            ).toString()

            assertTrue(result.contains("fun user("))
            assertTrue(result.contains("block: UserDslBuilder.() -> Unit"))
        }

        @Test
        fun `generates fields with arguments as functions`() {
            val result = generateQueryDsl(
                """
                type Query {
                    user(id: ID!): User
                }
                type User {
                    id: ID
                }
                """.trimIndent()
            ).toString()

            assertTrue(result.contains("fun user("))
            assertTrue(result.contains("id:"))
        }

        @Test
        fun `generates input type arguments as Map`() {
            val result = generateQueryDsl(
                """
                type Query {
                    users(filter: UserFilter): [User]
                }
                input UserFilter {
                    name: String
                }
                type User {
                    id: ID
                }
                """.trimIndent()
            ).toString()

            assertTrue(result.contains("fun users("))
            assertTrue(result.contains("filter: Map<String, Any?>"))
        }

        @Test
        fun `generates interface fields with builder block`() {
            val result = generateQueryDsl(
                """
                type Query {
                    node(id: ID!): Node
                }
                interface Node {
                    id: ID!
                }
                type User implements Node {
                    id: ID!
                }
                """.trimIndent()
            ).toString()

            assertTrue(result.contains("fun node("))
            assertTrue(result.contains("block: NodeDslBuilder.() -> Unit"))
        }

        @Test
        fun `generates alias parameter for complex fields`() {
            val result = generateQueryDsl(
                """
                type Query {
                    user: User
                }
                type User {
                    id: ID
                }
                """.trimIndent()
            ).toString()

            assertTrue(result.contains("alias: String? = null"))
        }
    }

    @Nested
    @DisplayName("Serialization Methods")
    inner class SerializationTests {

        @Test
        fun `generates serializeValue method`() {
            val result = generateQueryDsl(
                """
                type Query {
                    hello: String
                }
                """.trimIndent()
            ).toString()

            assertTrue(result.contains("private fun serializeValue(value: Any?): String"))
        }

        @Test
        fun `handles Map serialization in serializeValue`() {
            val result = generateQueryDsl(
                """
                type Query {
                    hello: String
                }
                """.trimIndent()
            ).toString()

            assertTrue(result.contains("is Map<*, *>"))
        }
    }
}
