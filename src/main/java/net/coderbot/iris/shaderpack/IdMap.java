package net.coderbot.iris.shaderpack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.coderbot.iris.Iris;
import net.minecraft.util.registry.Registry;
import org.apache.logging.log4j.Level;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;

/**
 * A utility class for parsing entries in item.properties, block.properties, and entities.properties files in shaderpacks
 */
public class IdMap {
	/**
	 * Maps a given item ID to an integer ID
	 */
	private final Object2IntMap<Identifier> itemIdMap;

	/**
	 * Maps a given entity ID to an integer ID
	 */
	private final Object2IntMap<Identifier> entityIdMap;

	/**
	 * A map that contains the identifier of an item to the integer value parsed in block.properties
	 */
	private Map<Identifier, Integer> blockPropertiesMap = Maps.newHashMap();

	/**
	 * A map that contains render layers for blocks in block.properties
	 */
	private Map<Identifier, RenderLayer> blockRenderLayerMap = Maps.newHashMap();

	public IdMap(Path shaderPath) {
		itemIdMap = loadProperties(shaderPath, "item.properties")
			.map(IdMap::parseItemIdMap).orElse(Object2IntMaps.emptyMap());

		entityIdMap = loadProperties(shaderPath, "entity.properties")
			.map(IdMap::parseEntityIdMap).orElse(Object2IntMaps.emptyMap());

		loadProperties(shaderPath, "block.properties").ifPresent(blockProperties -> {
			// TODO: This won't parse block states in block.properties properly
			blockPropertiesMap = parseIdMap(blockProperties, "block.", "block.properties");
			blockRenderLayerMap = parseRenderLayerMap(blockProperties, "layer.", "block.properties");
		});

		// TODO: Properly override block render layers
	}

	/**
	 * Loads properties from a properties file in a shaderpack path
	 * TODO: Preprocess conditional preprocessor directives (#ifdef, #if, etc.) and Standard Macros A to G from Optifine
	 * See https://github.com/sp614x/optifine/blob/master/OptiFineDoc/doc/shaders.txt#L670
	 * Permalink: https://github.com/sp614x/optifine/blob/28172bc21b306334e06916d3e3907f251c51e0dc/OptiFineDoc/doc/shaders.txt#L670
	 * This Java macro preprocessor might help: http://jsesoft.sourceforge.net/
	 */
	private static Optional<Properties> loadProperties(Path shaderPath, String name) {
		// TODO: Tempfix so pre-1.13 block id mappings in the currently unrecognized #else condition in
		//  Sildur's Shaders block.properties are ignored, so they don't override the valid post-1.13
		//  block id mappings in the #if MC_VERSION >= 11300 condition. Remove when conditionals
		//  and standard macro preprocessing is implemented.

		// Ignore duplicate properties after the original property is defined
		Properties properties = new Properties() {
			@Override
			public synchronized Object put(Object key, Object value) {
				if (get(key) != null) return get(key); // If the key already has a value, don't change it

				return super.put(key, value);
			}
		};

		if (shaderPath == null) return Optional.empty();

		try {
			properties.load(Files.newInputStream(shaderPath.resolve(name)));
		} catch (IOException e) {
			Iris.logger.debug("An " + name + " file was not found in the current shaderpack");

			return Optional.empty();
		}

		return Optional.of(properties);
	}

	private static Object2IntMap<Identifier> parseItemIdMap(Properties properties) {
		return parseIdMap(properties, "item.", "item.properties");
	}

	private static Object2IntMap<Identifier> parseEntityIdMap(Properties properties) {
		return parseIdMap(properties, "entity.", "entity.properties");
	}

	/**
	 * Parses an identifier map in OptiFine format
	 */
	private static Object2IntMap<Identifier> parseIdMap(Properties properties, String keyPrefix, String fileName) {
		Object2IntMap<Identifier> idMap = new Object2IntOpenHashMap<>();

		properties.forEach((keyObject, valueObject) -> {
			String key = (String) keyObject;
			String value = (String) valueObject;

			if (!key.startsWith(keyPrefix)) {
				// Not a valid line, ignore it
				return;
			}

			int intId;

			try {
				intId = Integer.parseInt(key.substring(keyPrefix.length()));
			} catch (NumberFormatException e) {
				// Not a valid property line
				Iris.logger.warn("Failed to parse line in " + fileName + ": invalid key " + key);
				return;
			}

			for (String part : value.split(" ")) {
				try {
					Identifier identifier = new Identifier(part);

					// TODO: Tempfix so pre-1.13 block id mappings in the currently unrecognized #else condition in
					//  Sildur's Shaders block.properties are ignored, so they don't override the valid post-1.13
					//  block id mappings in the #if MC_VERSION >= 11300 condition. Remove when conditionals
					//  and standard macro preprocessing is implemented.

					// Skip iteration if the block identifier doesn't exist in the registry (so non-existing pre-1.13
					// block id mappings in Sildur's won't be parsed)
					if (keyPrefix.equals("block.") && !Registry.BLOCK.getOrEmpty(identifier).isPresent()) continue;

					idMap.put(identifier, intId);
				} catch (InvalidIdentifierException e) {
					Iris.logger.warn("Failed to parse an identifier in " + fileName + " for the key " + key + ":");
					Iris.logger.catching(Level.WARN, e);
				}
			}
		});

		return Object2IntMaps.unmodifiable(idMap);
	}

	/**
	 * Parses a render layer map
	 */
	private static Map<Identifier, RenderLayer> parseRenderLayerMap(Properties properties, String keyPrefix, String fileName) {
		// TODO: Most of this is copied from parseIdMap, it would be nice to reduce duplication.
		Map<Identifier, RenderLayer> layerMap = new HashMap<>();

		properties.forEach((keyObject, valueObject) -> {
			String key = (String) keyObject;
			String value = (String) valueObject;

			if (!key.startsWith(keyPrefix)) {
				// Not a valid line, ignore it
				return;
			}

			RenderLayer layer;

			// See: https://github.com/sp614x/optifine/blob/master/OptiFineDoc/doc/shaders.txt#L556-L576
			switch (key) {
				case "solid":
					layer = RenderLayer.getSolid();
					break;
				case "cutout":
					layer = RenderLayer.getCutout();
					break;
				case "cutout_mipped":
					layer = RenderLayer.getCutoutMipped();
					break;
				case "translucent":
					layer = RenderLayer.getTranslucent();
					break;
				default:
					Iris.logger.warn("Failed to parse line in " + fileName + ": invalid render layer type: " + key);
					return;
			}

			for (String part : value.split(" ")) {
				try {
					Identifier identifier = new Identifier(part);

					layerMap.put(identifier, layer);
				} catch (InvalidIdentifierException e) {
					Iris.logger.warn("Failed to parse an identifier in " + fileName + " for the key " + key + ":");
					Iris.logger.catching(Level.WARN, e);
				}
			}
		});

		return layerMap;
	}

	public Map<Identifier, Integer> getBlockProperties() {
		return blockPropertiesMap;
	}

	public Map<Identifier, Integer> getItemIdMap() {
		return itemIdMap;
	}

	public Map<Identifier, Integer> getEntityIdMap() {
		return entityIdMap;
	}
}
