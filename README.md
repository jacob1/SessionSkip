Simple velocity proxy plugin, that allows keeping server in a mixed online mode. By default, all players are authenticated, but for players that don't own the game, they can be whitelisted by IP and will bypass Mojang authentication servers.

Usage:
- /exempt add &lt;player&gt; &lt;cidr&gt;
  - Exempts a player
- /exempt remove &lt;player&gt; &lt;cidr&gt;
  - Removes an existing exemption
- /exempt removeall &lt;player&gt;
  - Removes all exemptions on a player. You'll also need to migrate their inventory from an offline UUID to an online UUID.
- /exempt list &lt;player&gt;
  - List all exemptions on a player
- /exempt listplayers
  - List all players that have exemptions
- /exempt reload
  - Reload config from disk