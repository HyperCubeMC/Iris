package net.coderbot.iris.shaderpack;

import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.coderbot.iris.Iris;
import net.coderbot.iris.shaderpack.texture.CustomTextureData;
import net.coderbot.iris.shaderpack.texture.TextureFilteringData;
import net.coderbot.iris.shaderpack.texture.TextureStage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

public class ShaderPack {
	private final Path root;
	private final ProgramSet base;
	@Nullable
	private final ProgramSet overworld;
	private final ProgramSet nether;
	private final ProgramSet end;

	private final IdMap idMap;
	private final LanguageMap languageMap;
	private final Object2ObjectMap<TextureStage, Object2ObjectMap<String, CustomTextureData>> customTextureDataMap = new Object2ObjectOpenHashMap<>();
	private final CustomTextureData customNoiseTexture;
	private final ShaderPackConfig config;
	private final ShaderProperties shaderProperties;

	/**
	 * Reads a shader pack from the disk.
	 *
	 * @param root The path to the "shaders" directory within the shader pack
	 * @throws IOException
	 */
	public ShaderPack(Path root) throws IOException {
		// A null path is not allowed.
		Objects.requireNonNull(root);

		this.root = root;

		this.shaderProperties = loadProperties(root, "shaders.properties")
			.map(ShaderProperties::new)
			.orElseGet(ShaderProperties::empty);
		if (Iris.getIrisConfig() != null) {
			this.config = new ShaderPackConfig(Iris.getIrisConfig().getShaderPackName().orElse(""));
			this.config.load();
		} else this.config = null;

		this.base = new ProgramSet(root, root, shaderProperties, this);
		this.overworld = loadOverrides(root, "world0", shaderProperties, this);
		this.nether = loadOverrides(root, "world-1", shaderProperties, this);
		this.end = loadOverrides(root, "world1", shaderProperties, this);

		this.idMap = new IdMap(root);
		this.languageMap = new LanguageMap(root.resolve("lang"));

		customNoiseTexture = shaderProperties.getNoiseTexturePath().map(path -> {
			try {
				return readTexture(path);
			} catch (IOException e) {
				Iris.logger.error("Unable to read the custom noise texture at " + path, e);

				return null;
			}
		}).orElse(null);

		shaderProperties.getCustomTextures().forEach((textureStage, customTexturePropertiesMap) -> {
			Object2ObjectMap<String, CustomTextureData> innerCustomTextureDataMap = new Object2ObjectOpenHashMap<>();
			customTexturePropertiesMap.forEach((samplerName, path) -> {
				try {
					innerCustomTextureDataMap.put(samplerName, readTexture(path));
				} catch (IOException e) {
					Iris.logger.error("Unable to read the custom texture at " + path, e);
				}
			});

			customTextureDataMap.put(textureStage, innerCustomTextureDataMap);
		});

		if (this.config != null) this.config.save();
	}

	@Nullable
	private static ProgramSet loadOverrides(Path root, String subfolder, ShaderProperties shaderProperties, ShaderPack pack) throws IOException {
		if (root == null) {
			return new ProgramSet(null, null, shaderProperties, pack);
		}

		Path sub = root.resolve(subfolder);

		if (Files.exists(sub)) {
			return new ProgramSet(sub, root, shaderProperties, pack);
		}

		return null;
	}

	// TODO: Copy-paste from IdMap, find a way to deduplicate this
	private static Optional<Properties> loadProperties(Path shaderPath, String name) {
		Properties properties = new Properties();

		if (shaderPath == null) return Optional.empty();

		try {
			// NB: shaders.properties is specified to be encoded with ISO-8859-1 by OptiFine,
			//     so we don't need to do the UTF-8 workaround here.
			properties.load(Files.newInputStream(shaderPath.resolve(name)));
		} catch (IOException e) {
			Iris.logger.debug("An " + name + " file was not found in the current shaderpack");

			return Optional.empty();
		}

		return Optional.of(properties);
	}

	// TODO: Implement raw texture data types
	public CustomTextureData readTexture(String path) throws IOException {
		CustomTextureData customTextureData;
		if (path.contains(":") && ResourceLocation.isValidResourceLocation(path)) {
			customTextureData = new CustomTextureData.ResourceData(new ResourceLocation(path));
		} else {
			// TODO: Make sure the resulting path is within the shaderpack?
			if (path.startsWith("/")) {
				// NB: This does not guarantee the resulting path is in the shaderpack as a double slash could be used,
				// this just fixes shaderpacks like Continuum 2.0.4 that use a leading slash in texture paths
				path = path.substring(1);
			}

			boolean blur = false;
			boolean clamp = false;

			String mcMetaPath = path + ".mcmeta";
			if (Files.exists(root.resolve(mcMetaPath))) {
				JsonObject meta = GsonHelper.parse(new BufferedReader(new InputStreamReader(Files.newInputStream(root.resolve(mcMetaPath)), StandardCharsets.UTF_8)));
				blur = meta.get("texture").getAsJsonObject().get("blur").getAsBoolean();
				clamp = meta.get("texture").getAsJsonObject().get("clamp").getAsBoolean();
			}

			byte[] content = Files.readAllBytes(root.resolve(path));

			customTextureData = new CustomTextureData.PngData(new TextureFilteringData(blur, clamp), content);
		}
		return customTextureData;
	}

	public ProgramSet getProgramSet(DimensionId dimension) {
		ProgramSet overrides;

		switch (dimension) {
			case OVERWORLD:
				overrides = overworld;
				break;
			case NETHER:
				overrides = nether;
				break;
			case END:
				overrides = end;
				break;
			default:
				throw new IllegalArgumentException("Unknown dimension " + dimension);
		}

		return ProgramSet.merged(base, overrides);
	}

	public IdMap getIdMap() {
		return idMap;
	}

	public Object2ObjectMap<TextureStage, Object2ObjectMap<String, CustomTextureData>> getCustomTextureDataMap() {
		return customTextureDataMap;
	}

	public Optional<CustomTextureData> getCustomNoiseTexture() {
		return Optional.ofNullable(customNoiseTexture);
	}

	public ShaderProperties getShaderProperties() {
		return shaderProperties;
	}

	public ShaderPackConfig getConfig() {
		return config;
	}

	public LanguageMap getLanguageMap() {
		return languageMap;
	}
}
