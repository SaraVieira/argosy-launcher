package com.nendo.argosy.data.remote.github

import com.nendo.argosy.BuildConfig
import com.squareup.moshi.Moshi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class GitHubApiReleasesPathTest {

    private val server = MockWebServer()

    private val api: GitHubApi by lazy {
        Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
            .build()
            .create(GitHubApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `the update source requests the configured repository's releases page`() = runBlocking {
        server.enqueue(MockResponse().setBody("[]"))

        api.getRepoReleases(BuildConfig.UPDATE_REPO_OWNER, BuildConfig.UPDATE_REPO_NAME, perPage = 10, page = 2)

        assertEquals(
            "/repos/${BuildConfig.UPDATE_REPO_OWNER}/${BuildConfig.UPDATE_REPO_NAME}/releases?per_page=10&page=2",
            server.takeRequest().path
        )
    }

    @Test
    fun `the page defaults to the first`() = runBlocking {
        server.enqueue(MockResponse().setBody("[]"))

        api.getRepoReleases("owner", "repo", perPage = 10)

        assertEquals("/repos/owner/repo/releases?per_page=10&page=1", server.takeRequest().path)
    }
}
