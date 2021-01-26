package net.coderbot.iris.shaderpack.config;

import static net.coderbot.iris.Iris.SHADERPACK_DIR;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;

import net.coderbot.iris.Iris;

public class ShaderPackConfig {

	public final Path shaderPackConfigPath;
	private final Properties shaderProperties;
	private final String comment;

	private final Map<String, Option<Boolean>> booleanOptions = new HashMap<>();
	private final Map<String, Option<Float>> floatOptions = new HashMap<>();
	private final Map<String, Option<Integer>> integerOptions = new HashMap<>();

	public ShaderPackConfig(String name) {
		//optifine uses txt files, so we should do the same
		shaderPackConfigPath = SHADERPACK_DIR.resolve(name + ".txt");
		shaderProperties = new Properties();
		comment = "This file stores the shaderpack configuration for the shaderpack " + name;
	}

	public Properties getShaderProperties() {
		return shaderProperties;
	}

	//this 3 methods below should be used by the gui to get the available options and then use them
	public Map<String, Option<Boolean>> getBooleanOptions() {
		return booleanOptions;
	}

	public Map<String, Option<Float>> getFloatOptions() {
		return floatOptions;
	}

	public Map<String, Option<Integer>> getIntegerOptions() {
		return integerOptions;
	}

	/**
	 * Process a new option and adds it to the shader properties to be serialized
	 * @param option the option to process
	 * @param deserializer the function that converts a string to the desired type
	 * @param <T> the type of the Option
	 * @return a modified option that has read it's value
	 */
	public <T> Option<T> processOption(Option<T> option, Function<String, T> deserializer) {
		if (shaderProperties.containsKey(option.getName())) {
			option.load(shaderProperties, deserializer);
		}
		option.save(shaderProperties);
		return option;
	}


	public void load() throws IOException {
		if (!Files.exists(shaderPackConfigPath)) {
			return;
		}
		shaderProperties.load(Files.newInputStream(shaderPackConfigPath));
	}

	public void save() throws IOException {
		shaderProperties.store(Files.newOutputStream(shaderPackConfigPath), comment);
	}

}
