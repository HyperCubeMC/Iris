package net.coderbot.iris.gui.screen;

import com.google.common.base.Throwables;
import com.mojang.blaze3d.vertex.PoseStack;
import net.coderbot.iris.Iris;
import net.coderbot.iris.config.IrisConfig;
import net.coderbot.iris.gui.GuiUtil;
import net.coderbot.iris.gui.ScreenStack;
import net.coderbot.iris.gui.element.IrisObjectSelectionList;
import net.coderbot.iris.gui.element.PropertyDocumentWidget;
import net.coderbot.iris.gui.element.ShaderPackSelectionList;
import net.coderbot.iris.gui.property.BooleanOptionProperty;
import net.coderbot.iris.gui.property.FloatOptionProperty;
import net.coderbot.iris.gui.property.IntOptionProperty;
import net.coderbot.iris.gui.property.OptionProperty;
import net.coderbot.iris.gui.property.PropertyList;
import net.coderbot.iris.gui.property.StringOptionProperty;
import net.coderbot.iris.shaderpack.Option;
import net.coderbot.iris.shaderpack.ShaderPack;
import net.coderbot.iris.shaderpack.ShaderPackConfig;
import net.coderbot.iris.shaderpack.ShaderProperties;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class ShaderPackScreen extends Screen implements HudHideable {
	private static final Component SELECT_TITLE = new TranslatableComponent("pack.iris.select.title").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
	private static final Component CONFIGURE_TITLE = new TranslatableComponent("pack.iris.configure.title").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);

	private final Screen parent;

	private ShaderPackSelectionList shaderPackList;
	private PropertyDocumentWidget shaderProperties;

	private Component addedPackDialog = null;
	private int addedPackDialogTimer = 0;

	private boolean dropChanges = false;

	public ShaderPackScreen(Screen parent) {
		super(new TranslatableComponent("options.iris.shaderPackSelection.title"));

		this.parent = parent;
	}

	@Override
	public void render(PoseStack poseStack, int mouseX, int mouseY, float delta) {
		if (this.minecraft.level == null) {
			this.renderBackground(poseStack);
		} else {
			this.fillGradient(poseStack, 0, 0, width, height, 0x4F232323, 0x4F232323);
		}

		this.shaderPackList.render(poseStack, mouseX, mouseY, delta);
		this.shaderProperties.render(poseStack, mouseX, mouseY, delta);

		super.render(poseStack, mouseX, mouseY, delta);

		drawCenteredString(poseStack, this.font, this.title, (int)(this.width * 0.5), 8, 0xFFFFFF);

		if (addedPackDialog != null && addedPackDialogTimer > 0) {
			drawCenteredString(poseStack, this.font, addedPackDialog, (int) (this.width * 0.5), 21, 0xFFFFFF);
		} else {
			drawCenteredString(poseStack, this.font, SELECT_TITLE, (int) (this.width * 0.25), 21, 16777215);
			drawCenteredString(poseStack, this.font, CONFIGURE_TITLE, (int) (this.width * 0.75), 21, 16777215);
		}
	}

	@Override
	protected void init() {
		super.init();
		int bottomCenter = this.width / 2 - 50;
		int topCenter = this.width / 2 - 76;
		boolean inWorld = this.minecraft.level != null;

		this.shaderPackList = new ShaderPackSelectionList(this.minecraft, this.width / 2, this.height, 32, this.height - 58, 0, this.width / 2);

		if (inWorld) {
			this.shaderPackList.setRenderBackground(false);
		}

		this.addRenderableWidget(shaderPackList);

		this.refreshShaderPropertiesWidget();

		this.addRenderableWidget(new Button(bottomCenter + 104, this.height - 27, 100, 20,
				CommonComponents.GUI_DONE, button -> onClose()));

		this.addRenderableWidget(new Button(bottomCenter, this.height - 27, 100, 20,
				new TranslatableComponent("options.iris.apply"), button -> this.applyChanges()));

		this.addRenderableWidget(new Button(bottomCenter - 104, this.height - 27, 100, 20,
				CommonComponents.GUI_CANCEL, button -> this.dropChangesAndClose()));

		this.addRenderableWidget(new Button(topCenter - 78, this.height - 51, 152, 20,
				new TranslatableComponent("options.iris.openShaderPackFolder"), button -> openShaderPackFolder()));

		this.addRenderableWidget(new Button(topCenter + 78, this.height - 51, 152, 20,
				new TranslatableComponent("options.iris.refreshShaderPacks"), button -> this.shaderPackList.refresh()));

		this.addRenderableWidget(new IrisConfigScreenButton(this.width - 26, 6, button -> minecraft.setScreen(new IrisConfigScreen(this))));

		if (parent != null) {
			ScreenStack.push(parent);
		}
	}

	@Override
	public void tick() {
		super.tick();
		for (GuiEventListener e : this.children()) {
			if (e instanceof IrisObjectSelectionList) ((IrisObjectSelectionList) e).tick();
		}

		if (this.addedPackDialogTimer > 0) {
			this.addedPackDialogTimer--;
		}
	}

	@Override
	public void onFilesDrop(List<Path> paths) {
		List<Path> packs = paths.stream().filter(Iris::isValidShaderpack).collect(Collectors.toList());

		for (Path pack : packs) {
			String fileName = pack.getFileName().toString();

			try {
				Iris.getShaderpacksDirectoryManager().copyPackIntoDirectory(fileName, pack);
			} catch (FileAlreadyExistsException e) {
				this.addedPackDialog = new TranslatableComponent(
						"options.iris.shaderPackSelection.copyErrorAlreadyExists",
						fileName
				).withStyle(ChatFormatting.ITALIC, ChatFormatting.RED);

				this.addedPackDialogTimer = 100;
				this.shaderPackList.refresh();

				return;
			} catch (IOException e) {
				Iris.logger.warn("Error copying dragged shader pack", e);

				this.addedPackDialog = new TranslatableComponent(
						"options.iris.shaderPackSelection.copyError",
						fileName
				).withStyle(ChatFormatting.ITALIC, ChatFormatting.RED);

				this.addedPackDialogTimer = 100;
				this.shaderPackList.refresh();

				return;
			}
		}

		// After copying the relevant files over to the folder, make sure to refresh the shader pack list.
		this.shaderPackList.refresh();

		if (packs.size() == 0) {
			// If zero packs were added, then notify the user that the files that they added weren't actually shader
			// packs.

			if (paths.size() == 1) {
				// If a single pack could not be added, provide a message with that pack in the file name
				String fileName = paths.get(0).getFileName().toString();

				this.addedPackDialog = new TranslatableComponent(
						"options.iris.shaderPackSelection.failedAddSingle",
						fileName
				).withStyle(ChatFormatting.ITALIC, ChatFormatting.RED);
			} else {
				// Otherwise, show a generic message.

				this.addedPackDialog = new TranslatableComponent(
						"options.iris.shaderPackSelection.failedAdd"
				).withStyle(ChatFormatting.ITALIC, ChatFormatting.RED);
			}

		} else if (packs.size() == 1) {
			// In most cases, users will drag a single pack into the selection menu. So, let's special case it.
			String packName = packs.get(0).getFileName().toString();

			this.addedPackDialog = new TranslatableComponent(
					"options.iris.shaderPackSelection.addedPack",
					packName
			).withStyle(ChatFormatting.ITALIC, ChatFormatting.YELLOW);

			// Select the pack that the user just added, since if a user just dragged a pack in, they'll probably want
			// to actually use that pack afterwards.
			this.shaderPackList.select(packName);
		} else {
			// We also support multiple packs being dragged and dropped at a time. Just show a generic success message
			// in that case.
			this.addedPackDialog = new TranslatableComponent(
					"options.iris.shaderPackSelection.addedPacks",
					packs.size()
			).withStyle(ChatFormatting.ITALIC, ChatFormatting.YELLOW);
		}

		// Show the relevant message for 5 seconds (100 ticks)
		this.addedPackDialogTimer = 100;
	}

	@Override
	public void onClose() {
		if (!dropChanges) {
			applyChanges();
		}

		ScreenStack.pull(this.getClass());
		this.minecraft.setScreen(ScreenStack.pop());
	}

	private void dropChangesAndClose() {
		dropChanges = true;
		onClose();
	}

	private void applyChanges() {
		ShaderPackSelectionList.BaseEntry base = this.shaderPackList.getSelected();

		if (!(base instanceof ShaderPackSelectionList.ShaderPackEntry)) {
			return;
		}

		ShaderPackSelectionList.ShaderPackEntry entry = (ShaderPackSelectionList.ShaderPackEntry)base;
		IrisConfig config = Iris.getIrisConfig();

		String name = entry.getPackName();
		boolean changed = this.shaderProperties.saveProperties();
		boolean buttonEnabled = this.shaderPackList.getEnableShadersButton().enabled;
		if (config.areShadersEnabled() == buttonEnabled && name.equals(config.getShaderPackName().orElse("")) && !changed) return;

		config.setShaderPackName(name);
		// Not using IrisApi.getInstance().getConfig().setShadersEnabledAndApply due to its lack of sending a message to the user in chat notifying them of the failure to compile
		// IrisApi.getInstance().getConfig().setShadersEnabledAndApply(buttonEnabled);
		config.setShadersEnabled(buttonEnabled);

		try {
			config.save();
		} catch (IOException e) {
			Iris.logger.error("Error saving configuration file!");
			Iris.logger.catching(e);
		}

		try {
			Iris.reload();
		} catch (IOException e) {
			Iris.logger.error("Error reloading shader pack while applying changes!");
			Iris.logger.catching(e);

			if (this.minecraft.player != null) {
				this.minecraft.player.displayClientMessage(new TranslatableComponent("iris.shaders.reloaded.failure", Throwables.getRootCause(e).getMessage()).withStyle(ChatFormatting.RED), false);
			}
		}
		this.reloadShaderConfig();
	}

	private void refreshShaderPropertiesWidget() {
		this.removeWidget(shaderProperties);

		float scrollAmount = 0.0f;
		String page = "screen";

		if (this.shaderProperties != null) {
			scrollAmount = (float) this.shaderProperties.getScrollAmount() / this.shaderProperties.getMaxScroll();
			page = this.shaderProperties.getCurrentPage();
		}
		if (shaderProperties != null) this.shaderProperties.saveProperties();
		this.shaderProperties = new PropertyDocumentWidget(this.minecraft, this.width / 2, this.height, 32, this.height - 58, this.width / 2, this.width, 26);
		shaderProperties.onSave(() -> {
			ShaderPack shaderPack = Iris.getCurrentPack().orElse(null);
			if (shaderPack == null) {
				return false;
			}

			AtomicBoolean propertiesChanged = new AtomicBoolean(false);
			AtomicReference<String> newProfileName = new AtomicReference<>();

			ShaderPackConfig config = shaderPack.getConfig();
			for (String pageName : shaderProperties.getPages()) {
				PropertyList propertyList = shaderProperties.getPage(pageName);
				propertyList.forEvery(property -> {
					if (property instanceof OptionProperty) {
						String key = ((OptionProperty<?>) property).getKey();
						if (property instanceof IntOptionProperty) {
							Option<Integer> opt = config.getIntegerOption(key);
							if (opt != null && !opt.getValue().equals(((IntOptionProperty) property).getValue())) {
								opt.setValue(((IntOptionProperty) property).getValue());
								opt.save(config.getConfigProperties());
								propertiesChanged.set(true);
							}
						} else if (property instanceof FloatOptionProperty) {
							Option<Float> opt = config.getFloatOption(key);
							if (opt != null && !opt.getValue().equals(((OptionProperty<?>) property).getValue())) {
								opt.setValue(((FloatOptionProperty) property).getValue());
								opt.save(config.getConfigProperties());
								propertiesChanged.set(true);
							}
						} else if (property instanceof BooleanOptionProperty) {
							Option<Boolean> opt = config.getBooleanOption(key);
							if (opt != null && !opt.getValue().equals(((BooleanOptionProperty) property).getValue())) {
								opt.setValue(((BooleanOptionProperty) property).getValue());
								opt.save(config.getConfigProperties());
								propertiesChanged.set(true);
							}
						} else if (property instanceof StringOptionProperty) {
							if (!((StringOptionProperty) property).getKey().equals("<profile>")) return;

							String currentProfile = (String) config.getConfigProperties().get(key);
							if (currentProfile == null || !currentProfile.equals(((StringOptionProperty) property).getValue())) {
								newProfileName.set(((StringOptionProperty) property).getValue());
							}
						}
					}
				});
			}

			if (newProfileName.get() != null) {
				boolean changedPropertiesForProfile = setProfile(config, shaderPack.getShaderProperties(), newProfileName.get(), new ArrayList<>(), false);
				if (changedPropertiesForProfile) {
					propertiesChanged.set(true);
				}
			}

			try {
				config.save();
				config.load();
			} catch (IOException e) {
				e.printStackTrace();
			}

			return propertiesChanged.get();
		});
		shaderProperties.onLoad(() -> {
			ShaderPack shaderPack = Iris.getCurrentPack().orElse(null);
			if (shaderPack == null) {
				return;
			}

			ShaderPackConfig config = shaderPack.getConfig();
			for (String pageName : shaderProperties.getPages()) {
				PropertyList propertyList = shaderProperties.getPage(pageName);
				propertyList.forEvery(property -> {
					if (property instanceof OptionProperty) {
						String key = ((OptionProperty<?>) property).getKey();
						if (property instanceof IntOptionProperty) {
							Option<Integer> opt = config.getIntegerOption(key);
							if (opt != null) ((IntOptionProperty) property).setValue(opt.getValue());
						} else if (property instanceof FloatOptionProperty) {
							Option<Float> opt = config.getFloatOption(key);
							if (opt != null) ((FloatOptionProperty) property).setValue(opt.getValue());
						} else if (property instanceof BooleanOptionProperty) {
							Option<Boolean> opt = config.getBooleanOption(key);
							if (opt != null) ((BooleanOptionProperty) property).setValue(opt.getValue());
						} else if (property instanceof StringOptionProperty) {
							if (!((StringOptionProperty) property).getKey().equals("<profile>") || !config.getConfigProperties().containsKey(key)) return;

							((StringOptionProperty) property).setValue((String) config.getConfigProperties().get(key));
						}
					}
				});
			}
			try {
				config.save();
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
		if (this.minecraft.level != null) this.shaderProperties.setRenderBackground(false);
		this.reloadShaderConfig();

		this.shaderProperties.goTo(page);
		this.shaderProperties.setScrollAmount(this.shaderProperties.getMaxScroll() * scrollAmount);

		this.addRenderableWidget(shaderProperties);
	}

	private boolean setProfile(ShaderPackConfig config, ShaderProperties shaderProperties, String profileName, List<String> alreadyIncludedProfiles, boolean includeOnly) {
		AtomicBoolean propertiesChanged = new AtomicBoolean(false);
		if (!includeOnly) {
			config.getConfigProperties().put("<profile>", profileName);
		}
		Object profile = shaderProperties.asProperties().get(profileName);
		if (profile == null) {
			Iris.logger.warn("Failed to " + (includeOnly ? "include" : "set") + " shaderpack profile " + profileName + " as it does not exist!");
			return false;
		}
		String profileSettings = profile.toString();
		List<String> profileSettingsList = Arrays.asList(profileSettings.split(" "));
		profileSettingsList.forEach(setting -> {
			if (setting.contains("=")) {
				String[] parts = setting.split("=");
				if (parts.length == 2) {
					String settingKey = parts[0];
					String settingValue = parts[1];

					try {
						int value = Integer.parseInt(settingValue);
						Option<Integer> opt = config.getIntegerOption(settingKey);
						if (opt != null && opt.getValue() != value) {
							opt.setValue(value);
							opt.save(config.getConfigProperties());
							propertiesChanged.set(true);
						}
					} catch (NumberFormatException e) {
						try {
							float value = Float.parseFloat(settingValue);
							Option<Float> opt = config.getFloatOption(settingKey);
							if (opt != null && opt.getValue() != value) {
								opt.setValue(value);
								opt.save(config.getConfigProperties());
								propertiesChanged.set(true);
							}
						} catch (NumberFormatException ex) {
							Iris.logger.warn("Value type is not a valid integer or float for setting" + setting + " of profile " + profileName + ". This setting will not be set.");
						}
					}
				}
			} else if (setting.startsWith("!")) {
				Option<Boolean> opt = config.getBooleanOption(setting.substring(1));
				if (opt != null && opt.getValue()) {
					opt.setValue(false);
					opt.save(config.getConfigProperties());
					propertiesChanged.set(true);
				}
			} else if (setting.startsWith("profile.")) {
				if (!alreadyIncludedProfiles.contains(setting)) {
					alreadyIncludedProfiles.add(setting);
					boolean changedPropertiesForProfile = setProfile(config, shaderProperties, setting, alreadyIncludedProfiles, true);
					if (changedPropertiesForProfile) {
						propertiesChanged.set(true);
					}
				}
			} else {
				Option<Boolean> opt = config.getBooleanOption(setting);
				if (opt != null && !opt.getValue()) {
					opt.setValue(true);
					opt.save(config.getConfigProperties());
					propertiesChanged.set(true);
				}
			}
		});
		return propertiesChanged.get();
	}

	private void reloadShaderConfig() {
		ShaderPack shaderPack = Iris.getCurrentPack().orElse(null);
		if (shaderPack == null) {
			this.shaderProperties.setDocument(PropertyDocumentWidget.createShaderpackConfigDocument(this.minecraft.font, this.width / 2, "Shaders Disabled", null, this.shaderProperties), "screen");
			shaderProperties.loadProperties();
			return;
		}
		this.shaderProperties.setDocument(PropertyDocumentWidget.createShaderpackConfigDocument(this.minecraft.font, this.width / 2, Iris.getIrisConfig().getShaderPackName().orElse("Unnamed Shaderpack"), shaderPack, this.shaderProperties), "screen");
		shaderProperties.loadProperties();
	}

	public class IrisConfigScreenButton extends Button {
		public IrisConfigScreenButton(int x, int y, OnPress press) {
			super(x, y, 20, 20, new TranslatableComponent("tooltip.iris.config"), press);
		}

		@Override
		public void render(PoseStack poseStack, int mouseX, int mouseY, float delta) {
			GuiUtil.bindIrisWidgetsTexture();
			blit(poseStack, x, y, isMouseOver(mouseX, mouseY) ? 20 : 0, 0, 20, 20);

			if (isMouseOver(mouseX, mouseY)) {
				renderTooltip(poseStack, new TranslatableComponent("tooltip.iris.config"), mouseX, mouseY);
			}
		}
	}

	private void openShaderPackFolder() {
		Util.getPlatform().openUri(Iris.getShaderpacksDirectoryManager().getDirectoryUri());
	}
}
