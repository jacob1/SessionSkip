package us.starcatcher;

/**
 * Messages used in ExemptCommand
 *
 * @author jacob1 2025-09-07
 */
public interface Messages {

	String USAGE = "Usage: /exempt [add|remove|removeall|list|listplayers|reload] <player> <cidr>";

	String LIST_EXEMPTIONS = "There players are exempted: %s";
	String NO_EXEMPTIONS = "There are no exemptions";

	String NOT_EXEMPTED = "Player '%s' is not exempted";
	String IS_EXEMPTED = "Player '%s' is exempted from the following IPs: %s";

	String CONFIG_FAIL = "Exception while saving config to disk";
	String RELOAD_FAIL = "I/O Exception while reloading";

	String REMOVE_ALL = "Removed all exemptions";
	String REMOVE_ALL_FAIL = "No exemptions to remove";

	String INVALID_CIDR = "Invalid cidr";
	String ADD_CIDR = "Added exemption '%s@%s'";
	String ADD_CIDR_FAIL = "Did not add exemption '%s@%s', already exists";
	String REMOVE_CIDR = "Removed exemption '%s@%s'";
	String REMOVE_CIDR_FAIL = "Did not remove exemption '%s@%s', doesn't exist";

	String RELOAD_SUCCESS = "Reloaded, %d players exempted";
}
