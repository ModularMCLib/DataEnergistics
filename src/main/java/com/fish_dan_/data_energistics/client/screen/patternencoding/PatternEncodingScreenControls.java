package com.fish_dan_.data_energistics.client.screen.patternencoding;

/**
 * Binds encoding controls after the real screen and any wireless subclass have completed initialization.
 * This client-thread bridge lets the screen event reach the Mixin's widget access without overriding
 * AE's init method, which other integrations also extend. Rebinding follows every native screen init.
 */
public interface PatternEncodingScreenControls {

    /**
     * Attaches the upload window and source control to the initialized screen. Called once per
     * ScreenEvent.Init.Post, including resize and return from a native amount dialog; mutates UI state.
     */
    void data_energistics$initializeEncodingControls();
}
