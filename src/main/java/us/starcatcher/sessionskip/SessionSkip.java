package us.starcatcher.sessionskip;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;

public class SessionSkip extends Plugin implements Listener
{
    protected boolean debug;
    protected boolean enabled;
    protected List<String> skipNames = new ArrayList<>();
    protected List<String> skipIPs = new ArrayList<>();

    Configuration getConfig()
    {
        try
        {
            return ConfigurationProvider.getProvider(YamlConfiguration.class).load(new File(getDataFolder(), "config.yml"));
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void onEnable()
    {
        this.debug = this.getConfig().getBoolean("debug", true);
        this.getProxy().getLogger().log( Level.INFO, "[SessionSkip] Debug output set to {0}.", this.debug);
        
        this.enabled = this.getConfig().getBoolean("enabled", true);
        this.getProxy().getLogger().log( Level.INFO, "[SessionSkip] Plugin state is set to {0}.", this.enabled);

        Collection skiplist = this.getConfig().getList("skiplist");
        this.getProxy().getLogger().log( Level.INFO, "[SessionSkip] Loaded {0} skip rules.", skiplist.size());
        for (Object skip : skiplist)
        {
            String[] split = skip.toString().split("@");
            if (split.length != 2)
            {
            	this.getProxy().getLogger().log( Level.INFO, "[SessionSkip] Ignoring invalid skip rule: {0}", skip.toString());
            	continue;
            }
            this.skipNames.add(split[0]);
            this.skipIPs.add(split[1]);
        }
        
        this.getProxy().getPluginManager().registerListener(this, this);
        
        this.getProxy().getPluginManager().registerCommand(this, new SessionSkipCommand(this));
    }
    
    @Override
    public void onDisable()
    {
        this.debug = true;
        this.enabled = false;
        this.skipNames.clear();
        this.skipIPs.clear();
        
        this.getProxy().getPluginManager().unregisterListeners(this);
        
        this.getProxy().getPluginManager().unregisterCommands(this);
    }
    
    @EventHandler
    public void onAsyncPreLoginEvent(PreLoginEvent e)
    {
        PendingConnection handler = (PendingConnection)e.getConnection();
        
        if (!this.enabled)
        {
            this.getProxy().getLogger().log( Level.INFO, "[SessionSkip] Authenticating player {0} ({1}) since SessionSkip is not enabled in the config.", new Object[]{ handler.getName(), handler.getAddress().toString() } );
        }
        
        if (this.debug)
        {
            this.getProxy().getLogger().log(Level.INFO, "[SessionSkip] Connection from {0} via remote IP: {1}", new Object[]{ handler.getName(), handler.getAddress().getAddress().getHostAddress() });
        }

        for (int i = 0; i < this.skipNames.size(); i++)
        {
            String name = this.skipNames.get(i);
            String IP = this.skipIPs.get(i);
            if (name.equals(handler.getName()) && handler.getAddress().getAddress().getHostAddress().startsWith(IP))
            {
                handler.setOnlineMode(false);
                this.getProxy().getLogger().log(Level.INFO, "[SessionSkip] Skipping session server authentication for player {0} ({1}) since remote IP matched {2}", new Object[]{ name, IP });
                return;
            }
        }

        if (this.debug)
        {
            this.getProxy().getLogger().log( Level.INFO, "[SessionSkip] Authenticating player {0} ({1}) since no skip rules matched.", new Object[]{ handler.getName(), handler.getAddress().toString() });
        }
    }
}
