package viaduct.tenant.codegen.dsl

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import viaduct.codegen.st.STContents
import viaduct.graphql.schema.ViaductSchema
import viaduct.tenant.codegen.bytecode.config.ViaductBaseTypeMapper
import viaduct.tenant.codegen.kotlingen.bytecode.mkSchema

class QueryDslGenTest {
    private fun genQuery(sdl: String): STContents {
        val schema = mkSchema(sdl)
        val baseTypeMapper = ViaductBaseTypeMapper(schema)
        val queryType = schema.types["Query"]!! as ViaductSchema.Object
        return queryDslGen("com.example.test", queryType, baseTypeMapper)
    }

    @Test
    fun `generates query function`() {
        val result = genQuery(
            """
            type Query {
                hello: String
            }
            """.trimIndent()
        ).toString()

        assertTrue(result.contains("fun query(name: String? = null, block: QueryDslBuilder.() -> Unit): String"))
    }

    @Test
    fun `generates QueryDslBuilder class`() {
        val result = genQuery(
            """
            type Query {
                hello: String
            }
            """.trimIndent()
        ).toString()

        assertTrue(result.contains("class QueryDslBuilder internal constructor()"))
    }

    @Test
    fun `generates scalar fields as properties`() {
        val result = genQuery(
            """
            type Query {
                greeting: String
                count: Int
            }
            """.trimIndent()
        ).toString()

        assertTrue(result.contains("val greeting: Unit"))
        assertTrue(result.contains("val count: Unit"))
        assertTrue(result.contains("addField(\"greeting\")"))
        assertTrue(result.contains("addField(\"count\")"))
    }

    @Test
    fun `generates complex fields as functions`() {
        val result = genQuery(
            """
            type Query {
                user: User
            }
            type User {
                id: ID
            }
            """.trimIndent()
        ).toString()

        assertTrue(result.contains("fun user(block: UserDslBuilder.() -> Unit)"))
    }

    @Test
    fun `generates fields with arguments`() {
        val result = genQuery(
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
        assertTrue(result.contains("id: String"))
    }

    @Test
    fun `generates build method`() {
        val result = genQuery(
            """
            type Query {
                hello: String
            }
            """.trimIndent()
        ).toString()

        assertTrue(result.contains("internal fun build(): String = fields.joinToString(\" \")"))
    }

    @Test
    fun `generates serializeValue method`() {
        val result = genQuery(
            """
            type Query {
                hello: String
            }
            """.trimIndent()
        ).toString()

        assertTrue(result.contains("private fun serializeValue(value: Any?): String"))
    }
}

class MutationDslGenTest {
    private fun genMutation(sdl: String): STContents {
        val schema = mkSchema(sdl)
        val baseTypeMapper = ViaductBaseTypeMapper(schema)
        val mutationType = schema.types["Mutation"]!! as ViaductSchema.Object
        return mutationDslGen("com.example.test", mutationType, baseTypeMapper)
    }

    @Test
    fun `generates mutation function`() {
        val result = genMutation(
            """
            type Query { empty: Int }
            type Mutation {
                updateUser: Boolean
            }
            """.trimIndent()
        ).toString()

        assertTrue(result.contains("fun mutation(name: String? = null, block: MutationDslBuilder.() -> Unit): String"))
    }

    @Test
    fun `generates MutationDslBuilder class`() {
        val result = genMutation(
            """
            type Query { empty: Int }
            type Mutation {
                updateUser: Boolean
            }
            """.trimIndent()
        ).toString()

        assertTrue(result.contains("class MutationDslBuilder internal constructor()"))
    }

    @Test
    fun `generates scalar mutation fields`() {
        val result = genMutation(
            """
            type Query { empty: Int }
            type Mutation {
                deleteUser(id: ID!): Boolean
            }
            """.trimIndent()
        ).toString()

        assertTrue(result.contains("fun deleteUser("))
    }

    @Test
    fun `generates complex mutation fields`() {
        val result = genMutation(
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
}

class ObjectDslGenTest {
    private fun genObject(sdl: String, typename: String): STContents {
        val schema = mkSchema(sdl)
        val baseTypeMapper = ViaductBaseTypeMapper(schema)
        val objectType = schema.types[typename]!! as ViaductSchema.Object
        return objectDslGen("com.example.test", objectType, baseTypeMapper)
    }

    @Test
    fun `generates object builder class`() {
        val result = genObject(
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
    fun `generates scalar fields as properties`() {
        val result = genObject(
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
    fun `generates complex fields as functions`() {
        val result = genObject(
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
        val result = genObject(
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
        assertTrue(result.contains("limit: Int"))
    }
}

class NodeInterfaceDslGenTest {
    private fun genInterface(sdl: String, interfaceName: String): STContents {
        val schema = mkSchema(sdl)
        val interfaceType = schema.types[interfaceName]!! as ViaductSchema.Interface
        val implementingTypes = schema.types.values
            .filterIsInstance<ViaductSchema.Object>()
            .filter { obj -> obj.supers.any { it.name == interfaceName } }
        return nodeInterfaceDslGen("com.example.test", interfaceType, implementingTypes)
    }

    @Test
    fun `generates interface builder class`() {
        val result = genInterface(
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
        ).toString()

        assertTrue(result.contains("class NodeDslBuilder internal constructor()"))
    }

    @Test
    fun `generates common fields`() {
        val result = genInterface(
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
        ).toString()

        assertTrue(result.contains("val id: Unit"))
        assertTrue(result.contains("addField(\"id\")"))
    }

    @Test
    fun `generates fragment methods for implementing types`() {
        val result = genInterface(
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
        ).toString()

        assertTrue(result.contains("fun onUser(block: UserDslBuilder.() -> Unit)"))
        assertTrue(result.contains("fun onPost(block: PostDslBuilder.() -> Unit)"))
    }
}

class SubscriptionDslGenTest {
    private fun genSubscription(sdl: String): STContents {
        val schema = mkSchema(sdl)
        val baseTypeMapper = ViaductBaseTypeMapper(schema)
        val subscriptionType = schema.types["Subscription"]!! as ViaductSchema.Object
        // Using the function from DslFilesBuilder
        return subscriptionDslGen("com.example.test", subscriptionType, baseTypeMapper)
    }

    @Test
    fun `generates subscription function`() {
        val result = genSubscription(
            """
            type Query { empty: Int }
            type Subscription {
                messageAdded: String
            }
            """.trimIndent()
        ).toString()

        assertTrue(result.contains("fun subscription(name: String? = null, block: SubscriptionDslBuilder.() -> Unit): String"))
    }

    @Test
    fun `generates SubscriptionDslBuilder class`() {
        val result = genSubscription(
            """
            type Query { empty: Int }
            type Subscription {
                messageAdded: String
            }
            """.trimIndent()
        ).toString()

        assertTrue(result.contains("class SubscriptionDslBuilder internal constructor()"))
    }

    @Test
    fun `generates subscription fields`() {
        val result = genSubscription(
            """
            type Query { empty: Int }
            type Subscription {
                messageAdded: String
                userOnline(userId: ID!): Boolean
            }
            """.trimIndent()
        ).toString()

        assertTrue(result.contains("fun messageAdded("))
        assertTrue(result.contains("fun userOnline("))
    }
}
