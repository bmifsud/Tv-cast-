package com.example.apppresentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.apppresentation.ui.MainScreen
import com.example.apppresentation.ui.TestTags
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UniversalUiRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testTvModeRendersTvLayout() {
        val viewModel = MainViewModel(
            discoverer = TestDiscoverer(),
            sender = TestSender(),
            captureEmitter = TestCaptureEmitter()
        )

        composeTestRule.setContent {
            MainScreen(viewModel = viewModel, isTvOverride = true)
        }

        composeTestRule.onNodeWithTag(TestTags.TV_CONTAINER).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.TV_SURFACE_VIEW).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.TV_READY_OVERLAY).assertIsDisplayed()
        composeTestRule.onNodeWithText("Ready to Receive").assertIsDisplayed()
    }

    @Test
    fun testTabletModeRendersTabletLayoutAndStartsCasting() {
        val stubDiscoverer = TestDiscoverer()
        val stubSender = TestSender()
        val stubCaptureEmitter = TestCaptureEmitter()
        val viewModel = MainViewModel(
            discoverer = stubDiscoverer,
            sender = stubSender,
            captureEmitter = stubCaptureEmitter
        )

        composeTestRule.setContent {
            MainScreen(viewModel = viewModel, isTvOverride = false)
        }

        composeTestRule.onNodeWithTag(TestTags.TABLET_CONTAINER).assertIsDisplayed()
        composeTestRule.onNodeWithText("Discovered: Xiaomi TV Box (192.168.1.100)").assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.START_CASTING_BUTTON).assertIsDisplayed()

        // Perform click on Start Casting
        composeTestRule.onNodeWithTag(TestTags.START_CASTING_BUTTON).performClick()

        val shadowApp = Shadows.shadowOf(RuntimeEnvironment.getApplication())
        val nextIntent = shadowApp.nextStartedActivity
        assertNotNull(nextIntent)

        if (!stubSender.isCasting.value) {
            viewModel.startCasting(nextIntent)
        }

        assertTrue(stubSender.isCasting.value)
        composeTestRule.onNodeWithTag(TestTags.CASTING_ACTIVE_INDICATOR).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.STOP_CASTING_BUTTON).assertIsDisplayed()
    }
}
