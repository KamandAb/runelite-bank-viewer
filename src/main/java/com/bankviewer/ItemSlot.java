package com.bankviewer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.QuantityFormatter;

/**
 * A single item slot displayed in the bank viewer grid.
 * Must be constructed and mutated on the Swing Event Dispatch Thread (EDT).
 */
public class ItemSlot extends JPanel
{
	static final int SLOT_WIDTH = 56;
	static final int SLOT_HEIGHT_NO_NAME = 56;
	static final int SLOT_HEIGHT_WITH_NAME = 72;

	private final JLabel iconLabel;
	private final JLabel quantityLabel;
	private final JLabel nameLabel;

	public ItemSlot(boolean showName)
	{
		setLayout(new BorderLayout(0, 0));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createLineBorder(ColorScheme.BORDER_COLOR, 1));

		int height = showName ? SLOT_HEIGHT_WITH_NAME : SLOT_HEIGHT_NO_NAME;
		setPreferredSize(new Dimension(SLOT_WIDTH, height));
		setMinimumSize(new Dimension(SLOT_WIDTH, height));
		setMaximumSize(new Dimension(SLOT_WIDTH, height));

		// Icon label — centered in the cell
		iconLabel = new JLabel();
		iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
		iconLabel.setVerticalAlignment(SwingConstants.CENTER);

		// Quantity label — overlaid in the top-left corner
		quantityLabel = new JLabel();
		quantityLabel.setForeground(Color.YELLOW);
		quantityLabel.setFont(FontManager.getRunescapeSmallFont());
		quantityLabel.setHorizontalAlignment(SwingConstants.LEFT);
		quantityLabel.setVerticalAlignment(SwingConstants.TOP);

		// Stack icon and quantity in a layered sub-panel
		JPanel iconPanel = new JPanel(new BorderLayout(0, 0));
		iconPanel.setOpaque(false);
		iconPanel.add(iconLabel, BorderLayout.CENTER);
		iconPanel.add(quantityLabel, BorderLayout.NORTH);

		add(iconPanel, BorderLayout.CENTER);

		// Optional item name below icon
		nameLabel = new JLabel();
		nameLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		nameLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(9f));
		nameLabel.setHorizontalAlignment(SwingConstants.CENTER);
		nameLabel.setVisible(showName);

		if (showName)
		{
			add(nameLabel, BorderLayout.SOUTH);
		}
	}

	/**
	 * Updates this slot with item data and icon.
	 * AsyncBufferedImage.addTo() registers an internal repaint callback so the
	 * icon appears automatically when the sprite finishes loading.
	 *
	 * Must be called on the EDT.
	 */
	public void update(int itemId, String name, int quantity, AsyncBufferedImage image, boolean showName)
	{
		// addTo() calls setIcon and hooks a repaint callback on the label
		image.addTo(iconLabel);

		// Quantities of 1 look better without a number overlay
		if (quantity <= 1)
		{
			quantityLabel.setText("");
		}
		else
		{
			quantityLabel.setText(QuantityFormatter.quantityToStackSize(quantity));
		}

		if (showName && nameLabel.isVisible())
		{
			String display = name.length() > 8 ? name.substring(0, 7) + "." : name;
			nameLabel.setText(display);
		}

		setToolTipText(name + " x " + QuantityFormatter.formatNumber(quantity));
	}

	/**
	 * Resets this slot to an empty visual state.
	 * Must be called on the EDT.
	 */
	public void clear()
	{
		iconLabel.setIcon(null);
		quantityLabel.setText("");
		nameLabel.setText("");
		setToolTipText(null);
	}
}
