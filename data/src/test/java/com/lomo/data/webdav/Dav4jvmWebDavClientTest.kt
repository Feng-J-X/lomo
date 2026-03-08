package com.lomo.data.webdav

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class Dav4jvmWebDavClientTest {
    @Test
    fun `client disables automatic redirects for dav4jvm`() {
        val client = Dav4jvmWebDavClient("https://dav.example.com/root/", "alice", "secret")

        val field = Dav4jvmWebDavClient::class.java.getDeclaredField("httpClient")
        field.isAccessible = true
        val httpClient = field.get(client) as OkHttpClient

        assertFalse(httpClient.followRedirects)
        assertFalse(httpClient.followSslRedirects)
    }

    @Test
    fun `relative path resolves absolute encoded href`() {
        val client = Dav4jvmWebDavClient("https://dav.example.com/root/", "alice", "secret")

        assertEquals(
            "测试 memo.md",
            invokeRelativeToRoot(client, "https://dav.example.com/root/%E6%B5%8B%E8%AF%95%20memo.md"),
        )
    }

    @Test
    fun `relative path resolves path only href`() {
        val client = Dav4jvmWebDavClient("https://dav.example.com/root/", "alice", "secret")

        assertEquals("memo.md", invokeRelativeToRoot(client, "/root/memo.md"))
    }

    @Test
    fun `relative path rejects other hosts`() {
        val client = Dav4jvmWebDavClient("https://dav.example.com/root/", "alice", "secret")

        assertNull(invokeRelativeToRoot(client, "https://other.example.com/root/memo.md"))
    }

    private fun invokeRelativeToRoot(
        client: Dav4jvmWebDavClient,
        href: String,
    ): String? {
        val method = Dav4jvmWebDavClient::class.java.getDeclaredMethod("relativeToRoot", String::class.java)
        method.isAccessible = true
        return method.invoke(client, href) as String?
    }
}
