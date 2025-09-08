package us.starcatcher;

import com.google.inject.Inject;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import inet.ipaddr.IPAddressString;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sessionskip plugin
 *
 * @author jacob1 2025-09-07
 */
@Plugin(id = "sessionskip",
		name = "SessionSkip",
		version = "1.0-SNAPSHOT",
		description = "Allow players to skip Minecraft authentication",
		authors = { "jacob1" })
public class SessionSkip {

	/** List of exempt players */
	public Map<String, List<IPAddressString>> exemptPlayers = new HashMap<>();

	private final Logger logger;
	private final Path dataDirectory;
	private final ProxyServer proxy;

	private final String CONFIG_NAME = "sessionskip.yml";

	@Inject
	public SessionSkip(Logger logger, @DataDirectory Path dataDirectory, ProxyServer proxy) {
		this.logger = logger;
		this.dataDirectory = dataDirectory;
		this.proxy = proxy;
		System.out.println("Setup SessionSkip");
	}

	/** Initialize on velocity start */
	@Subscribe
	public void onProxyInitialization(ProxyInitializeEvent event) throws IOException {
		ensureConfigExists();
		loadConfig();
		saveConfig();
		initCommands();
		System.out.printf("Initialized SessionSkip, %d players exempted%n", exemptPlayers.size());
	}

	/** Event triggered when player tries to login */
	@Subscribe
	public void onAsyncPreLoginEvent(PreLoginEvent e) {
		var exemptedCidrs = exemptPlayers.get(e.getUsername());
		if (exemptedCidrs == null)
			return;

		logger.info("Checking session skip for user {}", e.getUsername());

		// Get IP and iterate through all IPs
		String ip = e.getConnection().getRemoteAddress().getAddress().getHostAddress();
		var ipString = new IPAddressString(ip);
		for (var cidr : exemptedCidrs) {
			if (cidr.contains(ipString)) {
				logger.info("{} matches cidr {}, skipping authentication", e.getUsername(), cidr);
				e.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
				return;
			}
		}

		logger.warn("{} is in session skip list, but didn't match any IP Ranges", e.getUsername());

		var msg = String.format("Your username is in the session skip list, but you don't match the IP Ranges. Ask an admin to add %s", ip);
		e.setResult(PreLoginEvent.PreLoginComponentResult.denied(Component.text(msg)));
	}

	/**
	 * Get path to the config file
	 *
	 * @return The config file path
	 */
	private Path getConfigPath() {
		return dataDirectory.resolve("sessionskip.yml");
	}

	/**
	 * Make sure config file exists. If not, create it.
	 *
	 * @throws IOException If some I/O issue happens
	 */
	private void ensureConfigExists() throws IOException {
		if (!Files.exists(dataDirectory))
			Files.createDirectory(dataDirectory);

		Path config = getConfigPath();
		if (!Files.exists(config)) {
			try (InputStream stream = this.getClass().getClassLoader().getResourceAsStream(CONFIG_NAME)) {
				if (stream == null)
					throw new RuntimeException("Couldn't find default " + CONFIG_NAME);

				Files.copy(stream, config);
			}
		}
	}

	/**
	 * Load config from disk and init exempted players
	 *
	 * @throws IOException If some I/O issue happens
	 */
	private void loadConfig() throws IOException {
		Path config = getConfigPath();
		var loader = YamlConfigurationLoader.builder().path(config).nodeStyle(NodeStyle.BLOCK).build();
		var rootNode = loader.load();
		var playersNode = rootNode.node("sessionskip", "players");

		var players = playersNode.getList(String.class, List.of());
		exemptPlayers = parsePlayersList(players);
	}

	/**
	 * Save config to disk. Called every time exempted players list is loaded or changed
	 *
	 * @throws IOException If some I/O issue happens
	 */
	private void saveConfig() throws IOException {
		Path config = getConfigPath();
		var loader = YamlConfigurationLoader.builder().path(config).nodeStyle(NodeStyle.BLOCK).build();
		var rootNode = loader.load();
		var playersNode = rootNode.node("sessionskip", "players");
		playersNode.setList(String.class, serializePlayersList());

		// Save back to config
		loader.save(rootNode);
	}

	/**
	 * Take list of exempted players and parse out CIDR data
	 *
	 * @param players The list of players, in player@cidr format
	 * @return The exempted players map
	 */
	private Map<String, List<IPAddressString>> parsePlayersList(List<String> players) {
		var exemptList = new HashMap<String, List<IPAddressString>>();

		if (players == null) {
			logger.warn("Got empty player list");
			return exemptList;
		}

		for (var player : players) {
			int atPos = player.lastIndexOf('@');
			if (atPos == -1) {
				logger.warn("Invalid sessionskip config, should be format username@cidr: {}", player);
				continue;
			}

			String username = player.substring(0, atPos);
			var cidr = new IPAddressString(player.substring(atPos + 1));
			exemptList.computeIfAbsent(username, k -> new ArrayList<>()).add(cidr);
		}

		return exemptList;
	}

	/**
	 * Convert exempted players list back into format for config file
	 *
	 * @return The list of exempted players, in config format
	 */
	private List<String> serializePlayersList() {
		List<String> players = new ArrayList<>();
		for (var exemptPlayer : exemptPlayers.entrySet()) {
			for (var cidr : exemptPlayer.getValue()) {
				players.add(String.format("%s@%s", exemptPlayer.getKey(), cidr.toString()));
			}
		}

		return players;
	}

	/**
	 * Initialize commands
	 */
	private void initCommands() {
		var commandManager = proxy.getCommandManager();
		var commandMetadata = commandManager.metaBuilder("exempt").plugin(this).build();

		commandManager.register(commandMetadata, new ExemptCommand(this));
	}

	/**
	 * Get all cidrs for a player
	 *
	 * @param player The player to check
	 * @return The list of cidrs for this player, or empty list if there are no exemptions
	 */
	protected List<IPAddressString> getCidrs(String player) {
		return exemptPlayers.getOrDefault(player, List.of());
	}

	/**
	 * Get all players with active exemptions
	 *
	 * @return The list of players with active exemptions
	 */
	protected List<String> getPlayers() {
		return new ArrayList<>(exemptPlayers.keySet());
	}

	/**
	 * Add a cidr exemption and make it active
	 *
	 * @param player The player to exempt
	 * @param cidr   The cidr to exempt
	 * @return true if a new exemption was added, false if the exemption wasn't added because it already exists
	 * @throws IOException If some I/O issue happens
	 */
	protected boolean addCidr(String player, IPAddressString cidr) throws IOException {
		var exemptions = exemptPlayers.computeIfAbsent(player, k -> new ArrayList<>());
		if (exemptions.contains(cidr))
			return false;

		exemptions.add(cidr);
		saveConfig();

		return true;
	}

	/**
	 * Removes a cidr exemption
	 *
	 * @param player The player to unexempt
	 * @param cidr   The cidr to unexempt
	 * @return true if the exemption was removed, false if the exemption wasn't removed because it didn't exist
	 * @throws IOException If some I/O issue happens
	 */
	protected boolean removeCidr(String player, IPAddressString cidr) throws IOException {
		var exemptions = exemptPlayers.computeIfAbsent(player, k -> new ArrayList<>());
		if (exemptions.contains(cidr)) {
			exemptions.remove(cidr);
			// Last exemption removed, remove it from map too
			if (exemptions.isEmpty())
				exemptPlayers.remove(player);

			saveConfig();

			return true;
		}

		return false;
	}

	/**
	 * Removes all exemptions for a player
	 *
	 * @param player The player to remove exemptions for
	 * @return True if exemptions were removed, false if nothing happened because that player had no exemptions
	 * @throws IOException If some I/O issue happens
	 */
	protected boolean removeAllCidrs(String player) throws IOException {
		if (exemptPlayers.containsKey(player)) {
			exemptPlayers.remove(player);
			saveConfig();

			return true;
		}

		return false;
	}

	/**
	 * Reload exemptions from disk
	 * @throws IOException If some I/O issue happens
	 */
	protected void reload() throws IOException {
		ensureConfigExists();
		loadConfig();
		saveConfig();
		logger.info("Reloaded SessionSkip, {} players exempted", exemptPlayers.size());
	}
}
