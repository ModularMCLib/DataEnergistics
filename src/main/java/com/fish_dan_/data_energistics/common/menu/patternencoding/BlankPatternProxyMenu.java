package com.fish_dan_.data_energistics.common.menu.patternencoding;

/**
 * Exposes the optional network-backed blank-pattern slot used by pattern encoding menus.
 *
 * <p>
 * Implemented by the encoding-menu Mixin, so clients must check for this capability before invoking the actions.
 * Whether to use the network slot is a global server-synchronized configuration setting. Actions run on the menu's
 * owning thread; client calls send menu actions to the server, which performs the storage operations.
 */
public interface BlankPatternProxyMenu {

    void data_energistics$depositCarriedBlankPatterns(boolean single);

    void data_energistics$pickupBlankPatterns(boolean single);
}
