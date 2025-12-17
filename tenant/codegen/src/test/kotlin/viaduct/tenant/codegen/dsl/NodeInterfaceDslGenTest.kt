package viaduct.tenant.codegen.dsl

import kotlin.test.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import viaduct.codegen.st.STContents
import viaduct.graphql.schema.ViaductSchema
import org.junit.jupiter.api.Assertions.assertNotNull
import viaduct.tenant.codegen.kotlingen.bytecode.mkSchema

class NodeInterfaceDslGenTest {

    private fun generateInterfaceDsl(sdl: String, interfaceName: String): STContents? {
        val schema = mkSchema(sdl)
        val interfaceType = schema.types[interfaceName] as? ViaductSchema.Interface
            ?: return null
        val implementingTypes = schema.types.values
            .filterIsInstance<ViaductSchema.Object>()
            .filter { obj -> obj.supers.any { it.name == interfaceName } }
        return nodeInterfaceDslGen(TestPackages.DSL_PACKAGE, interfaceType, implementingTypes)
    }

    @Nested
    @DisplayName("Builder Class Generation")
    inner class BuilderClassTests {

        @Test
        fun `generates interface builder class`() {
            val result = generateInterfaceDsl(
                """
                type Query { node(id: ID!): Node }
                interface Node {
                    id: ID!
                }
                type User implements Node {
                    id: ID!
                    name: String
                }
                """.trimIndent(),
                "Node"
            )?.toString()

            assertNotNull(result, "Expected interface DSL to be generated")
            assertTrue(result!!.contains("class NodeDslBuilder internal constructor()"))
        }
    }

    @Nested
    @DisplayName("Common Field Generation")
    inner class CommonFieldTests {

        @Test
        fun `generates common scalar fields as properties`() {
            val result = generateInterfaceDsl(
                """
                type Query { node(id: ID!): Node }
                interface Node {
                    id: ID!
                }
                type User implements Node {
                    id: ID!
                    name: String
                }
                """.trimIndent(),
                "Node"
            )?.toString()

            assertNotNull(result, "Expected interface DSL to be generated")
            assertTrue(result!!.contains("val id: Unit"))
            assertTrue(result.contains("addField(\"id\")"))
        }

        @Test
        fun `excludes complex fields from common fields`() {
            val result = generateInterfaceDsl(
                """
                type Query { node(id: ID!): Node }
                interface Node {
                    id: ID!
                    relatedNodes: [Node]
                }
                type User implements Node {
                    id: ID!
                    relatedNodes: [Node]
                }
                """.trimIndent(),
                "Node"
            )?.toString()

            assertNotNull(result, "Expected interface DSL to be generated")
            // Should have id as property
            assertTrue(result!!.contains("val id: Unit"))
            // Should NOT have relatedNodes as property (it's complex)
            assertFalse(result.contains("val relatedNodes: Unit"))
        }
    }

    @Nested
    @DisplayName("Fragment Method Generation")
    inner class FragmentMethodTests {

        @Test
        fun `generates fragment methods for implementing types`() {
            val result = generateInterfaceDsl(
                """
                type Query { node(id: ID!): Node }
                interface Node {
                    id: ID!
                }
                type User implements Node {
                    id: ID!
                    name: String
                }
                type Post implements Node {
                    id: ID!
                    title: String
                }
                """.trimIndent(),
                "Node"
            )?.toString()

            assertNotNull(result, "Expected interface DSL to be generated")
            assertTrue(result!!.contains("fun onUser(block: UserDslBuilder.() -> Unit)"))
            assertTrue(result.contains("fun onPost(block: PostDslBuilder.() -> Unit)"))
        }

        @Test
        fun `generates inline fragment syntax in fragment methods`() {
            val result = generateInterfaceDsl(
                """
                type Query { node(id: ID!): Node }
                interface Node {
                    id: ID!
                }
                type User implements Node {
                    id: ID!
                }
                """.trimIndent(),
                "Node"
            )?.toString()

            assertNotNull(result, "Expected interface DSL to be generated")
            assertTrue(result!!.contains("... on User"))
        }

        @Test
        fun `generates nested builder invocation in fragment methods`() {
            val result = generateInterfaceDsl(
                """
                type Query { node(id: ID!): Node }
                interface Node {
                    id: ID!
                }
                type User implements Node {
                    id: ID!
                }
                """.trimIndent(),
                "Node"
            )?.toString()

            assertNotNull(result, "Expected interface DSL to be generated")
            assertTrue(result!!.contains("val nestedBuilder = UserDslBuilder()"))
            assertTrue(result.contains("nestedBuilder.block()"))
        }
    }
}
