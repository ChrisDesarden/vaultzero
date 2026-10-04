package com.vaultzero.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaultzero.app.MainActivity
import com.vaultzero.app.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultAppTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun unlockScreen_isDisplayed() {
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.unlock_title)
        ).assertIsDisplayed()
    }

    @Test
    fun enterPassword_andUnlock() {
        val passwordLabel = composeTestRule.activity.getString(R.string.password)
        val unlockButton = composeTestRule.activity.getString(R.string.unlock)

        composeTestRule.onNodeWithText(passwordLabel).performTextInput("masterpass")
        composeTestRule.onNodeWithText(unlockButton).performClick()

        // After unlock, home screen should appear
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.empty_vault)
        ).assertIsDisplayed()
    }
}
