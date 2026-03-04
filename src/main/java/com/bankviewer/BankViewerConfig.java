package com.bankviewer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("bankviewer")
public interface BankViewerConfig extends Config
{
	@ConfigItem(
		keyName = "columns",
		name = "Grid Columns",
		description = "Number of item columns to display in the grid",
		position = 0
	)
	@Range(min = 2, max = 10)
	default int columns()
	{
		return 4;
	}

	@ConfigItem(
		keyName = "showGeValue",
		name = "Show GE Value",
		description = "Show estimated Grand Exchange value at the bottom of the panel",
		position = 1
	)
	default boolean showGeValue()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showItemNames",
		name = "Show Item Names",
		description = "Show item name below the icon in each slot",
		position = 2
	)
	default boolean showItemNames()
	{
		return false;
	}
}
