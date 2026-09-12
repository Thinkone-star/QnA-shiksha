package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("QnA Shiksha", appName)
  }

  @Test
  fun `test custom scheme deep link extraction`() {
    val activity = Robolectric.buildActivity(MainActivity::class.java).get()
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("qnashiksha://solution?id=c_seed_1"))
    val chapterId = activity.extractChapterIdFromIntent(intent)
    assertEquals("c_seed_1", chapterId)
  }

  @Test
  fun `test web url deep link extraction with query parameter`() {
    val activity = Robolectric.buildActivity(MainActivity::class.java).get()
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ais-pre-fekhv3edhwq73nhnidpiys-648137748503.asia-southeast1.run.app/?solution=c_seed_science_10"))
    val chapterId = activity.extractChapterIdFromIntent(intent)
    assertEquals("c_seed_science_10", chapterId)
  }

  @Test
  fun `test web url deep link extraction with hash fragment`() {
    val activity = Robolectric.buildActivity(MainActivity::class.java).get()
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://qnashiksha.com/#chapter=c_seed_math_9"))
    val chapterId = activity.extractChapterIdFromIntent(intent)
    assertEquals("c_seed_math_9", chapterId)
  }

  @Test
  fun `test path based solution deep link extraction`() {
    val activity = Robolectric.buildActivity(MainActivity::class.java).get()
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://qnashiksha.com/solution/c_seed_bio_12"))
    val chapterId = activity.extractChapterIdFromIntent(intent)
    assertEquals("c_seed_bio_12", chapterId)
  }
}
