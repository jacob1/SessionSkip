package us.starcatcher;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import inet.ipaddr.IPAddressString;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * /exempt command implementation
 *
 * @author jacob1 2025-09-07
 */
public class ExemptCommand implements SimpleCommand {

	/** Reference to main plugin */
	private final SessionSkip sessionSkip;

	/** List of all subcommands */
	private static final List<String> ALL_CMDS = List.of("add", "remove", "removeall", "list", "listplayers", "reload");
	/** List of subcommands that take player arguments */
	private static final List<String> PLAYER_ARG_CMDS = List.of("add", "remove", "removeall", "list");

	/**
	 * Constructor
	 *
	 * @param sessionSkip Reference to main plugin
	 */
	public ExemptCommand(SessionSkip sessionSkip) {
		this.sessionSkip = sessionSkip;
	}

	/**
	 * Called when command is executed
	 */
	@Override
	public void execute(Invocation invocation) {
		var source = invocation.source();
		var args = invocation.arguments();

		if (!tryExecute(source, args)) {
			source.sendPlainMessage(Messages.USAGE);
		}
	}

	/**
	 * Try to execute a subcommand
	 *
	 * @return false if no command was executed due to invalid usage
	 */
	private boolean tryExecute(CommandSource source, String[] args) {
		String cmd = args.length > 0 ? args[0].toLowerCase() : null;
		switch (args.length) {
			case 1:
				if (cmd.equals("listplayers")) {
					executeListPlayers(source);
					return true;
				} else if (cmd.equals("reload")) {
					executeReload(source);
					return true;
				}
				break;
			case 2:
				if (cmd.equals("removeall")) {
					executeRemoveAll(source, args[1]);
					return true;
				} else if (cmd.equals("list")) {
					executeList(source, args[1]);
					return true;
				}
				break;
			case 3:
				if (cmd.equals("add") || cmd.equals("remove")) {
					executeAddRemove(source, args[1], args[2], cmd.equals("add"));
					return true;
				}
				break;
		}

		return false;
	}

	/**
	 * /exempt listplayers
	 */
	private void executeListPlayers(CommandSource source) {
		var allPlayers = sessionSkip.getPlayers();
		if (!allPlayers.isEmpty())
			source.sendPlainMessage(Messages.LIST_EXEMPTIONS.formatted(String.join(", ", sessionSkip.getPlayers())));
		else
			source.sendPlainMessage(Messages.NO_EXEMPTIONS);
	}

	/**
	 * /exempt list &lt;player&gt;
	 */
	private void executeList(CommandSource source, String player) {
		var cidrs = sessionSkip.getCidrs(player).stream().map(IPAddressString::toString).toList();
		if (cidrs.isEmpty()) {
			source.sendPlainMessage(Messages.NOT_EXEMPTED.formatted(player));
		} else {
			var cidrsStr = String.join(", ", cidrs);
			source.sendPlainMessage(Messages.IS_EXEMPTED.formatted(player, cidrsStr));
		}
	}

	/**
	 * /exempt removeall &lt;player&gt;
	 */
	private void executeRemoveAll(CommandSource source, String player) {
		try {
			boolean success = sessionSkip.removeAllCidrs(player);
			source.sendPlainMessage(success ? Messages.REMOVE_ALL : Messages.REMOVE_ALL_FAIL);
		} catch (IOException e) {
			source.sendPlainMessage(Messages.CONFIG_FAIL);
		}
	}

	/**
	 * /exempt add &lt;player&gt; &lt;cidr&gt;<br/>
	 * /exempt remove &lt;player&gt; &lt;cidr&gt;
	 */
	private void executeAddRemove(CommandSource source, String player, String cidrStr, boolean isAdd) {
		var cidr = new IPAddressString(cidrStr);
		if (!cidr.isValid() || cidr.isEmpty()) {
			source.sendPlainMessage(Messages.INVALID_CIDR);
			return;
		}

		try {
			if (isAdd) {
				boolean success = sessionSkip.addCidr(player, cidr);
				source.sendPlainMessage((success ? Messages.ADD_CIDR : Messages.ADD_CIDR_FAIL).formatted(player, cidrStr));
			} else {
				boolean success = sessionSkip.removeCidr(player, cidr);
				source.sendPlainMessage((success ? Messages.REMOVE_CIDR : Messages.REMOVE_CIDR_FAIL).formatted(player, cidrStr));
			}
		} catch (IOException e) {
			source.sendPlainMessage(Messages.CONFIG_FAIL);
		}
	}

	/**
	 * /exempt reload
	 */
	private void executeReload(CommandSource source) {
		try {
			sessionSkip.reload();

			source.sendPlainMessage(Messages.RELOAD_SUCCESS.formatted(sessionSkip.getPlayers().size()));
		} catch (IOException e) {
			source.sendPlainMessage(Messages.RELOAD_FAIL);
		}
	}

	/**
	 * Handles autocomplete when running command
	 */
	@Override
	public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
		var args = invocation.arguments();
		if (args.length == 0) {
			return CompletableFuture.completedFuture(ALL_CMDS);
		} else if (args.length == 1) {
			return CompletableFuture.completedFuture(filterSuggestions(ALL_CMDS, args[0]));
		} else if (args.length == 2 && PLAYER_ARG_CMDS.contains(args[0])) {
			return CompletableFuture.completedFuture(filterSuggestions(sessionSkip.getPlayers(), args[1]));
		} else if (args.length == 3 && "remove".equals(args[0])) {
			var cidrs = sessionSkip.getCidrs(args[1]).stream().map(IPAddressString::toString).toList();
			return CompletableFuture.completedFuture(filterSuggestions(cidrs, args[2]));
		}
		return CompletableFuture.completedFuture(List.of());
	}

	private List<String> filterSuggestions(List<String> suggestions, String prefix) {
		if (prefix == null)
			return suggestions;

		return suggestions.stream().filter(s -> s.startsWith(prefix)).toList();
	}
	/**
	 * Checks if we have permission to run the command. A velocity-specific permission plugin is needed, because server op doesn't apply.
	 */
	@Override
	public boolean hasPermission(Invocation invocation) {
		return invocation.source().hasPermission("command.exempt");
	}
}
