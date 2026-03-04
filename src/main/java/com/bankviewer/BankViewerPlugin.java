package com.bankviewer;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Bank Viewer",
	description = "Displays your bank contents in a side panel with live quantity updates",
	tags = {"bank", "items", "inventory", "storage", "viewer"},
	enabledByDefault = false
)
public class BankViewerPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private BankViewerConfig config;

	private BankViewerPanel panel;
	private NavigationButton navButton;

	/**
	 * Bank-pin safety flag.
	 *
	 * Starts false on every new session. Set to true only when
	 * ItemContainerChanged fires for InventoryID.BANK — which RuneLite
	 * guarantees only after the bank pin has been validated and the bank
	 * interface is open.
	 *
	 * Reset to false on logout or world-hop so no stale data leaks across
	 * session boundaries.
	 */
	private boolean bankLoaded = false;

	// ── Plugin lifecycle ────────────────────────────────────────────────────

	/**
	 * Called on the EDT by RuneLite's PluginManager; Swing construction is safe here.
	 */
	@Override
	protected void startUp() throws Exception
	{
		log.debug("Bank Viewer starting");

		panel = new BankViewerPanel(itemManager, config);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");

		navButton = NavigationButton.builder()
			.tooltip("Bank Viewer")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
		log.debug("Bank Viewer started");
	}

	/**
	 * Called on the EDT by RuneLite's PluginManager.
	 */
	@Override
	protected void shutDown() throws Exception
	{
		log.debug("Bank Viewer shutting down");
		clientToolbar.removeNavigation(navButton);
		bankLoaded = false;
		panel = null;
		navButton = null;
		log.debug("Bank Viewer shut down");
	}

	// ── Event handlers (called on the game/client thread, NOT the EDT) ─────

	/**
	 * Handles bank container updates.
	 *
	 * ItemContainerChanged for InventoryID.BANK is only dispatched after the
	 * bank pin is validated and the bank interface is fully open. No additional
	 * pin-detection logic is required; the event itself is the safe signal.
	 *
	 * A defensive snapshot of Item primitives is built here on the game thread
	 * before crossing to the EDT via invokeLater.
	 */
	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.BANK.getId())
		{
			return;
		}

		bankLoaded = true;

		ItemContainer container = event.getItemContainer();
		if (container == null)
		{
			return;
		}

		// Build defensive snapshot on the game thread.
		// Empty slots have id == -1 and are excluded.
		Item[] rawItems = container.getItems();
		final List<Item> snapshot = new ArrayList<>();
		if (rawItems != null)
		{
			for (Item item : rawItems)
			{
				if (item != null && item.getId() != -1)
				{
					snapshot.add(item);
				}
			}
		}

		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.updateBankItems(snapshot);
			}
		});
	}

	/**
	 * Handles game state transitions.
	 *
	 * On LOGIN_SCREEN or HOPPING: clears the bankLoaded flag and wipes the
	 * panel so no stale bank data is visible across session boundaries.
	 */
	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			bankLoaded = false;
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					panel.clearPanel();
				}
			});
		}
	}

	@Provides
	BankViewerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BankViewerConfig.class);
	}
}
