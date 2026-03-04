package com.bankviewer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.QuantityFormatter;

/**
 * Side panel for the Bank Viewer plugin.
 *
 * Threading contract: all Swing mutations must occur on the EDT.
 * Public mutating methods (updateBankItems, clearPanel) must be called
 * via SwingUtilities.invokeLater() from the game thread.
 */
public class BankViewerPanel extends PluginPanel
{
	private static final String NO_BANK_MSG = "<html><center>Open your bank<br>to view contents</center></html>";

	private final ItemManager itemManager;
	private final BankViewerConfig config;

	// UI components
	private final JTextField searchBar;
	private final JPanel gridPanel;
	private final JScrollPane scrollPane;
	private final JLabel statusLabel;
	private final JLabel itemCountLabel;
	private final JLabel totalValueLabel;

	// Reusable slot pool to avoid excessive object creation
	private final List<ItemSlot> slotPool = new ArrayList<>();

	// Current state
	private final List<Item> currentItems = new ArrayList<>();
	private String currentFilter = "";

	/**
	 * Constructs the panel. Must be called on the EDT.
	 */
	public BankViewerPanel(ItemManager itemManager, BankViewerConfig config)
	{
		super(false);
		this.itemManager = itemManager;
		this.config = config;

		setLayout(new BorderLayout(0, 4));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(4, 4, 4, 4));

		// ── TOP: search bar ────────────────────────────────────────────────
		JPanel topPanel = new JPanel(new BorderLayout(4, 0));
		topPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		topPanel.setBorder(new EmptyBorder(4, 6, 4, 6));

		JLabel searchIcon = new JLabel("Filter:");
		searchIcon.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		searchIcon.setFont(FontManager.getRunescapeSmallFont());

		searchBar = new JTextField();
		searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR.brighter());
		searchBar.setForeground(Color.WHITE);
		searchBar.setCaretColor(Color.WHITE);
		searchBar.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
		searchBar.setFont(FontManager.getRunescapeSmallFont());

		searchBar.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				onFilterChanged();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				onFilterChanged();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				onFilterChanged();
			}
		});

		topPanel.add(searchIcon, BorderLayout.WEST);
		topPanel.add(searchBar, BorderLayout.CENTER);
		add(topPanel, BorderLayout.NORTH);

		// ── CENTER: status label (shown when bank not loaded) ──────────────
		statusLabel = new JLabel(NO_BANK_MSG, SwingConstants.CENTER);
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		statusLabel.setBorder(new EmptyBorder(20, 10, 20, 10));

		// ── CENTER: item grid (shown when bank is loaded) ──────────────────
		gridPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 2, 2));
		gridPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		scrollPane = new JScrollPane(gridPanel);
		scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
		scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

		// Default center: show status
		add(statusLabel, BorderLayout.CENTER);

		// ── BOTTOM: summary bar ────────────────────────────────────────────
		JPanel bottomPanel = new JPanel();
		bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
		bottomPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		bottomPanel.setBorder(new EmptyBorder(4, 6, 4, 6));

		itemCountLabel = new JLabel("Items: --");
		itemCountLabel.setForeground(ColorScheme.TEXT_COLOR);
		itemCountLabel.setFont(FontManager.getRunescapeSmallFont());

		totalValueLabel = new JLabel("Value: --");
		totalValueLabel.setForeground(new Color(255, 200, 0));
		totalValueLabel.setFont(FontManager.getRunescapeSmallFont());
		totalValueLabel.setVisible(config.showGeValue());

		bottomPanel.add(itemCountLabel);
		bottomPanel.add(Box.createVerticalStrut(2));
		bottomPanel.add(totalValueLabel);

		add(bottomPanel, BorderLayout.SOUTH);
	}

	// ── Public API (must be called via SwingUtilities.invokeLater) ─────────

	/**
	 * Replaces the displayed grid with a new set of bank items.
	 * The list is a defensive copy built on the game thread.
	 *
	 * Must be called on the EDT.
	 */
	public void updateBankItems(List<Item> items)
	{
		assert SwingUtilities.isEventDispatchThread();

		currentItems.clear();
		currentItems.addAll(items);
		showGrid();
		renderGrid();
		updateSummaryBar(items);
	}

	/**
	 * Clears all bank data and shows the "open your bank" prompt.
	 * Called on logout or world-hop.
	 *
	 * Must be called on the EDT.
	 */
	public void clearPanel()
	{
		assert SwingUtilities.isEventDispatchThread();

		currentItems.clear();
		currentFilter = "";
		searchBar.setText("");
		showStatus(NO_BANK_MSG);
		itemCountLabel.setText("Items: --");
		totalValueLabel.setText("Value: --");
		totalValueLabel.setVisible(config.showGeValue());
	}

	// ── Internal rendering helpers ─────────────────────────────────────────

	private void showGrid()
	{
		remove(statusLabel);
		add(scrollPane, BorderLayout.CENTER);
		revalidate();
		repaint();
	}

	private void showStatus(String message)
	{
		statusLabel.setText(message);
		remove(scrollPane);
		add(statusLabel, BorderLayout.CENTER);
		revalidate();
		repaint();
	}

	/**
	 * Rebuilds the item grid from currentItems filtered by currentFilter.
	 * Reuses ItemSlot instances from slotPool.
	 */
	private void renderGrid()
	{
		gridPanel.removeAll();

		List<Item> filtered = applyFilter(currentItems, currentFilter);
		boolean showNames = config.showItemNames();

		// Grow pool as needed
		while (slotPool.size() < filtered.size())
		{
			slotPool.add(new ItemSlot(showNames));
		}

		for (int i = 0; i < filtered.size(); i++)
		{
			Item item = filtered.get(i);
			ItemSlot slot = slotPool.get(i);

			ItemComposition comp = itemManager.getItemComposition(item.getId());
			int qty = item.getQuantity();

			// getImage() returns an AsyncBufferedImage; addTo(label) in ItemSlot
			// registers the label for automatic repaint when the image loads.
			AsyncBufferedImage image = itemManager.getImage(item.getId(), qty, qty > 1);
			slot.update(item.getId(), comp.getName(), qty, image, showNames);

			gridPanel.add(slot);
		}

		// Clear slots that are no longer needed
		for (int i = filtered.size(); i < slotPool.size(); i++)
		{
			slotPool.get(i).clear();
		}

		gridPanel.revalidate();
		gridPanel.repaint();
	}

	private void updateSummaryBar(List<Item> items)
	{
		int uniqueTypes = items.size();
		long totalQty = 0;
		for (Item item : items)
		{
			totalQty += item.getQuantity();
		}

		itemCountLabel.setText(
			"Items: " + uniqueTypes + " types / " + QuantityFormatter.formatNumber(totalQty));

		if (config.showGeValue())
		{
			long totalValue = 0;
			for (Item item : items)
			{
				int price = itemManager.getItemPrice(item.getId());
				totalValue += (long) price * item.getQuantity();
			}
			totalValueLabel.setText("Value: " + QuantityFormatter.quantityToStackSize(totalValue) + " gp");
			totalValueLabel.setVisible(true);
		}
		else
		{
			totalValueLabel.setVisible(false);
		}
	}

	private List<Item> applyFilter(List<Item> items, String filter)
	{
		if (filter == null || filter.isEmpty())
		{
			return new ArrayList<>(items);
		}

		String lower = filter.toLowerCase();
		List<Item> result = new ArrayList<>();
		for (Item item : items)
		{
			ItemComposition comp = itemManager.getItemComposition(item.getId());
			if (comp.getName().toLowerCase().contains(lower))
			{
				result.add(item);
			}
		}
		return result;
	}

	// DocumentListener fires on EDT, so no invokeLater needed here
	private void onFilterChanged()
	{
		currentFilter = searchBar.getText().trim();
		if (!currentItems.isEmpty())
		{
			renderGrid();
		}
	}
}
