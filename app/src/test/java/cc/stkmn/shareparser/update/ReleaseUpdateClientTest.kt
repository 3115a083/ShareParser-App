package cc.stkmn.shareparser.update

import java.io.*
import java.net.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.*
import kotlinx.coroutines.*
import org.junit.Test

class ReleaseUpdateClientTest {
    private class Connection(private val input:InputStream,private val code:Int=200):
        HttpURLConnection(URL("https://example.org")) {
        val disconnected=AtomicBoolean()
        override fun connect(){}
        override fun usingProxy()=false
        override fun disconnect(){disconnected.set(true);input.close()}
        override fun getResponseCode()=code
        override fun getInputStream()=input
    }
    @Test fun parsesReleaseAndClosesConnection()=runBlocking {
        val connection=Connection("""{"tag_name":"v0.4.3"}""".byteInputStream())
        assertEquals("v0.4.3",ReleaseUpdateClient{connection}.latestTag("test"))
        assertTrue(connection.disconnected.get())
        assertFalse(connection.instanceFollowRedirects)
    }
    @Test fun rejectsOversizeAndRedirects()=runBlocking {
        val body=ByteArray(ReleaseUpdateClient.MAX_BYTES+1){65}.inputStream()
        val large=Connection(body)
        assertFailsWith<IllegalArgumentException> {ReleaseUpdateClient{large}.latestTag("test")}
        assertTrue(large.disconnected.get())
        val redirect=Connection("{}".byteInputStream(),302)
        assertFailsWith<IllegalStateException> {ReleaseUpdateClient{redirect}.latestTag("test")}
        assertTrue(redirect.disconnected.get())
    }
    @Test fun cancellationClosesBlockedResponse()=runBlocking {
        val started=CountDownLatch(1)
        val release=CountDownLatch(1)
        val input=object:InputStream(){
            override fun read():Int {started.countDown();release.await(3,TimeUnit.SECONDS);return -1}
            override fun close(){release.countDown()}
        }
        val connection=Connection(input)
        val work=launch(Dispatchers.Default) {ReleaseUpdateClient{connection}.latestTag("test")}
        assertTrue(withContext(Dispatchers.IO){started.await(3,TimeUnit.SECONDS)})
        work.cancelAndJoin()
        assertTrue(connection.disconnected.get())
    }
    @Test fun acceptsExactReaderBoundary() {
        val data=ByteArray(ReleaseUpdateClient.MAX_BYTES){65}
        assertEquals(data.size,ReleaseUpdateClient.readBounded(data.inputStream()).length)
    }
}
